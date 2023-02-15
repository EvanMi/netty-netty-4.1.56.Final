/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.netty.example.ocsp;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.ReferenceCountedOpenSslContext;
import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.CharsetUtil;
import org.bouncycastle.openssl.bc.BcPEMDecryptorProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCSException;
import org.bouncycastle.pkcs.jcajce.JcePKCSPBEInputDecryptorProviderBuilder;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpHeaderNames.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaderValues.*;
import static io.netty.handler.codec.http.HttpHeaderValues.KEEP_ALIVE;

/**
 * ATTENTION: This is an incomplete example! In order to provide a fully functional
 * end-to-end example we'd need an X.509 certificate and the matching PrivateKey.
 */
@SuppressWarnings("unused")
public class OcspServerExample {
    public static void main(String[] args) throws Exception {
        // We assume there's a private key.
        //证书申请用的是sslforfree.com提供的免费证书，能用90天
        //但是首先要有一个域名~~一年花9块钱
        PrivateKey privateKey = parsePrivateKey(OcspServerExample.class, "private.key", null);

        // Step 1: Load the certificate chain for netty.io. We'll need the certificate
        // and the issuer's certificate and we don't need any of the intermediate certs.
        // The array is assumed to be a certain order to keep things simple.
        X509Certificate[] keyCertChain = parseCertificates(OcspServerExample.class, "yumi.pem");

        X509Certificate certificate = keyCertChain[0];
        X509Certificate issuer = keyCertChain[keyCertChain.length - 1];

        // Step 2: We need the URL of the CA's OCSP responder server. It's somewhere encoded
        // into the certificate! Notice that it's an HTTP URL.
        URI uri = OcspUtils.ocspUri(certificate);
        System.out.println("OCSP Responder URI: " + uri);

        if (uri == null) {
            throw new IllegalStateException("The CA/certificate doesn't have an OCSP responder");
        }

        // Step 3: Construct the OCSP request
        OCSPReq request = new OcspRequestBuilder()
                .certificate(certificate)
                .issuer(issuer)
                .build();

        // Step 4: Do the request to the CA's OCSP responder
        OCSPResp response = OcspUtils.request(uri, request, 5L, TimeUnit.SECONDS);
        if (response.getStatus() != OCSPResponseStatus.SUCCESSFUL) {
            throw new IllegalStateException("response-status=" + response.getStatus());
        }

        // Step 5: Is my certificate any good or has the CA revoked it?
        BasicOCSPResp basicResponse = (BasicOCSPResp) response.getResponseObject();
        SingleResp first = basicResponse.getResponses()[0];

        CertificateStatus status = first.getCertStatus();
        System.out.println("Status: " + (status == CertificateStatus.GOOD ? "Good" : status));
        System.out.println("This Update: " + first.getThisUpdate());
        System.out.println("Next Update: " + first.getNextUpdate());

        if (status != null) {
            throw new IllegalStateException("certificate-status=" + status);
        }

        BigInteger certSerial = certificate.getSerialNumber();
        BigInteger ocspSerial = first.getCertID().getSerialNumber();
        if (!certSerial.equals(ocspSerial)) {
            throw new IllegalStateException("Bad Serials=" + certSerial + " vs. " + ocspSerial);
        }

        // Step 6: Cache the OCSP response and use it as long as it's not
        // expired. The exact semantics are beyond the scope of this example.

        if (!OpenSsl.isAvailable()) {
            throw new IllegalStateException("OpenSSL is not available!");
        }

        if (!OpenSsl.isOcspSupported()) {
            throw new IllegalStateException("OCSP is not supported!");
        }

        if (privateKey == null) {
            throw new IllegalStateException("Because we don't have a PrivateKey we can't continue past this point.");
        }

        ReferenceCountedOpenSslContext context
            = (ReferenceCountedOpenSslContext) SslContextBuilder.forServer(privateKey, keyCertChain)
                .sslProvider(SslProvider.OPENSSL)
                .enableOcsp(true)
                .build();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup childrenGroup = new NioEventLoopGroup();
        try {
                    new ServerBootstrap()
                            .group(bossGroup, childrenGroup)
                            .channel(NioServerSocketChannel.class)
                            .handler(new LoggingHandler(LogLevel.INFO))
                            .childHandler(newServerHandler(context, response))
                            .bind(443)
                            .sync()
                            .channel()
                            .closeFuture()
                            .sync();
            // so on and so forth...又是没搞完的工程

        } finally {
            context.release();
        }
    }

    private static ChannelInitializer<Channel> newServerHandler(final ReferenceCountedOpenSslContext context,
            final OCSPResp response) {
        return new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel ch) throws Exception {
                SslHandler sslHandler = context.newHandler(ch.alloc());

                if (response != null) {
                    ReferenceCountedOpenSslEngine engine
                        = (ReferenceCountedOpenSslEngine) sslHandler.engine();

                    engine.setOcspResponse(response.getEncoded());
                }

                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(sslHandler);
                pipeline.addLast(new HttpServerCodec());
                pipeline.addLast(new HttpObjectAggregator(1024 * 1024));
                pipeline.addLast(new ServerHttpHandler());
                // so on and so forth...没搞完的工程，搞个简单的web服务器出来
            }
        };
    }


    private static class ServerHttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

        private static final byte[] CONTENT = { 'H', 'e', 'l', 'l', 'o', ' ', 'W', 'o', 'r', 'l', 'd' };

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
            System.out.println(msg.headers());
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(CONTENT));
            response.headers()
                    .set(CONTENT_TYPE, TEXT_PLAIN)
                    .setInt(CONTENT_LENGTH, response.content().readableBytes());
            boolean keepAlive = HttpUtil.isKeepAlive(msg);
            if (keepAlive) {
                if (!msg.protocolVersion().isKeepAliveDefault()) {
                    response.headers().set(CONNECTION, KEEP_ALIVE);
                }
            } else {
                // Tell the client we're going to close the connection.
                response.headers().set(CONNECTION, CLOSE);
            }

            ChannelFuture f = ctx.writeAndFlush(response);

            if (!keepAlive) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        }
    }


    private static X509Certificate[] parseCertificates(Class<?> clazz, String name) throws Exception {
        InputStream in = clazz.getResourceAsStream(name);
        if (in == null) {
            throw new FileNotFoundException("clazz=" + clazz + ", name=" + name);
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, CharsetUtil.US_ASCII));
            try {
                return parseCertificates(reader);
            } finally {
                reader.close();
            }
        } finally {
            in.close();
        }
    }


    private static PrivateKey parsePrivateKey(Class<?> clazz, String name, char[] password) throws Exception {
        InputStream in = clazz.getResourceAsStream(name);
        if (in == null) {
            throw new FileNotFoundException("clazz=" + clazz + ", name=" + name);
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, CharsetUtil.US_ASCII));
            PrivateKeyInfo pki;
            try {
                PEMParser parser = new PEMParser(reader);
                Object o = parser.readObject();
                if (o instanceof PKCS8EncryptedPrivateKeyInfo) { // encrypted private key in pkcs8-format
                    System.out.println("key in pkcs8 encoding");
                    PKCS8EncryptedPrivateKeyInfo epki = (PKCS8EncryptedPrivateKeyInfo) o;
                    System.out.println("encryption algorithm: " + epki.getEncryptionAlgorithm().getAlgorithm());
                    JcePKCSPBEInputDecryptorProviderBuilder builder =
                            new JcePKCSPBEInputDecryptorProviderBuilder().setProvider("BC");
                    InputDecryptorProvider idp = builder.build(password);
                    pki = epki.decryptPrivateKeyInfo(idp);
                } else if (o instanceof PEMEncryptedKeyPair) { // encrypted private key in pkcs1-format
                    System.out.println("key in pkcs1 encoding");
                    PEMEncryptedKeyPair epki = (PEMEncryptedKeyPair) o;
                    PEMKeyPair pkp = epki.decryptKeyPair(new BcPEMDecryptorProvider(password));
                    pki = pkp.getPrivateKeyInfo();
                } else if (o instanceof PEMKeyPair) { // unencrypted private key
                    System.out.println("key unencrypted");
                    PEMKeyPair pkp = (PEMKeyPair) o;
                    pki = pkp.getPrivateKeyInfo();
                } else {
                    throw new PKCSException("Invalid encrypted private key class: " + o.getClass().getName());
                }
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(new BouncyCastleProvider());
                return converter.getPrivateKey(pki);
            } finally {
                reader.close();
            }
        } finally {
            in.close();
        }
    }

    private static X509Certificate[] parseCertificates(Reader reader) throws Exception {

        JcaX509CertificateConverter converter = new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider());


        List<X509Certificate> dst = new ArrayList<X509Certificate>();

        PEMParser parser = new PEMParser(reader);
        try {
          X509CertificateHolder holder = null;

          while ((holder = (X509CertificateHolder) parser.readObject()) != null) {
            X509Certificate certificate = converter.getCertificate(holder);
            if (certificate == null) {
              continue;
            }

            dst.add(certificate);
          }
        } finally {
            parser.close();
        }

        return dst.toArray(new X509Certificate[0]);
    }
}
