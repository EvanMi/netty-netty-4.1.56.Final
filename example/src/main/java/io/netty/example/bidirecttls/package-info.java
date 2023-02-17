package io.netty.example.bidirecttls;

/**
 * 参考文章地址
 * https://www.cnblogs.com/codehello/articles/16924966.html
 * 创建一个自己的ca
 * 1. 生成CA的key
 * openssl genrsa -out ca.key 4096
 * 2. 生成ca证书签发请求ca.csr
 * openssl req -new -sha256 -out ca.csr -key ca.key -config ca.conf
 * 3. 生成ca.crt证书
 * openssl x509 -req -days 3650 -in ca.csr -signkey ca.key -out ca.crt
 *
 *
 * 生成服务端和客户端私钥 | 命令中需要输入密码测试可以都输入123456
 * 1. 生成server的key openssl genrsa -des3 -out server.key 1024
 * 2. 生成client的key openssl genrsa -des3 -out client.key 1024
 * 根据key生成csr文件 | -config openssl.cnf    # openssl的位置/usr/local/etc/openssl@1.1
 * 3. openssl req -new -key server.key -out server.csr -config openssl.cnf
 * 4. openssl req -new -key client.key -out client.csr -config openssl.cnf
 * 根据ca证书server.csr、client.csr生成x509证书
 * 5. openssl x509 -req -days 3650 -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out server.crt
 * 6. openssl x509 -req -days 3650 -in client.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out client.crt
 * 将key文件进行PKCS#8编码
 * 7. openssl pkcs8 -topk8 -in server.key -out pkcs8_server.key -nocrypt
 * 8. openssl pkcs8 -topk8 -in client.key -out pkcs8_client.key -nocrypt
 * */