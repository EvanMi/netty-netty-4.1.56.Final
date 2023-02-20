package io.netty.example.socksproxy;
/**
 * 依赖于pysocks
 *
 * import socks
 *
 * s = socks.socksocket()
 * s.set_proxy(socks.SOCKS4, "localhost")
 *
 * s.connect(("localhost", 8007))
 *
 * s.sendall("hello".encode("utf-8"))
 * print(s.recv(4096).decode('utf-8'))
 */