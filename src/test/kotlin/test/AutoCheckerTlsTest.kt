package test

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import icu.samnyan.aqua.spring.AutoChecker
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

/** The billing port serves a self-signed certificate without any subject alternative name. */
class AutoCheckerTlsTest : StringSpec({
    "the self-test client accepts a self-signed certificate that has no subject alternative name" {
        val dir = Files.createTempDirectory("autochecker-tls").toFile()
        val ks = File(dir, "ks.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool").path
        val rc = ProcessBuilder(keytool, "-genkeypair", "-alias", "t", "-keyalg", "RSA", "-keysize", "2048",
            "-dname", "CN=ib", "-validity", "2", "-storetype", "PKCS12", "-keystore", ks.path, "-storepass", "changeit")
            .redirectErrorStream(true).start().also { it.inputStream.readAllBytes() }.waitFor()
        rc shouldBe 0

        val store = KeyStore.getInstance("PKCS12").apply { ks.inputStream().use { load(it, "changeit".toCharArray()) } }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "changeit".toCharArray()) }
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            httpsConfigurator = HttpsConfigurator(SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) })
            createContext("/sys/test") { ex ->
                val body = "Server running".toByteArray()
                ex.sendResponseHeaders(200, body.size.toLong()); ex.responseBody.use { it.write(body) }
            }
            start()
        }
        try {
            val url = "https://127.0.0.1:${server.address.port}/sys/test"
            AutoChecker.trustAllRestTemplate().getForEntity(url, String::class.java).body shouldBe "Server running"
        } finally {
            server.stop(0); dir.deleteRecursively()
        }
    }
})
