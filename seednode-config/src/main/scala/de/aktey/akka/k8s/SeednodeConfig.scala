package de.aktey.akka.k8s

import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._
import scala.collection.JavaConverters._


import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions, ConfigSyntax}

import scala.io.Source

/** inspired by io.k8s.cassandra.KubernetesSeedProvider
  * of the https://github.com/kubernetes/kubernetes project
  */
object SeednodeConfig {

  private val env: String ⇒ Option[String] = sys.env.get

  val host = env("KUBERNETES_PORT_443_TCP_ADDR").getOrElse("kubernetes.default.svc.cluster.local")
  val port = env("KUBERNETES_PORT_443_TCP_PORT").getOrElse("443")
  val podNamespace = env("POD_NAMESPACE").getOrElse("default")
  val podIp = env("POD_IP").getOrElse("0.0.0.0")
  val accountToken = env("K8S_ACCOUNT_TOKEN").getOrElse("/var/run/secrets/kubernetes.io/serviceaccount/token")

  val serviceName = env("SERVICE_NAME")
    .getOrElse(throw new IllegalArgumentException("environment variable SERVICE_NAME is not set"))

  // TODO: Load the CA cert when it is available on all platforms.
  private val trustAll = Array[TrustManager](
    new X509TrustManager() {
      override def getAcceptedIssuers: Array[X509Certificate] = null

      override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()

      override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = ()
    }
  )

  private val trustAllHosts = new HostnameVerifier {
    override def verify(hostname: String, session: SSLSession) = true
  }

  /** get all available service IPs as potential seeds */
  def seedIps: List[String] = {
    val token = Source.fromFile(accountToken).mkString

    val sslCtx = SSLContext.getInstance("SSL")
    sslCtx.init(null, trustAll, new SecureRandom())

    // TODO: Remove this once the CA cert is propagated everywhere, and replace with loading the CA cert.
    val url = s"https://$host:$port/api/v1/namespaces/$podNamespace/endpoints/$serviceName"
    val conn = new URL(url).openConnection().asInstanceOf[HttpsURLConnection]
    conn.setHostnameVerifier(trustAllHosts)
    conn.setSSLSocketFactory(sslCtx.getSocketFactory)
    conn.addRequestProperty("Authorization", "Bearer " + token)

    val json = Source.fromInputStream(conn.getInputStream).mkString

    // using config, so I don't need another framework
    for {
      subsets <- ConfigFactory
        .parseString(json, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.JSON))
        .getConfigList("subsets").asScala.toList
      addresses <- subsets.getConfigList("addresses").asScala.toList
    } yield addresses.getString("ip")
  }

  /** return a config, that contains at most 5 seed node entries */
  def getConfig(systemName: String): Config = {
    val ipConf = seedIps.take(5).map(ip => s""""akka.tcp://$systemName@$ip:"$${akka.remote.netty.tcp.port}""")
    val cfg =
      s"""
         |akka {
         |  remote.netty.tcp.hostname = "$podIp"
         |  cluster.seed-nodes= [
         |    ${ipConf.mkString(",")}
         |  ]
         |}""".stripMargin

    ConfigFactory.parseString(cfg, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.CONF))
  }
}
