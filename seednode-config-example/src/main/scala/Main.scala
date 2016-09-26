import akka.actor._
import com.typesafe.config.ConfigFactory
import de.aktey.akka.k8s.SeednodeConfig

object Main extends App {

  val systemName = "akka-k8s"
  // create the seed node config
  val kubeConfig = SeednodeConfig.getConfig(systemName)
  // load the rest of the config
  // resolve the variables introduced by `K8sSeednodeConfig.getConfig`
  val config = kubeConfig.withFallback(ConfigFactory.load()).resolve()

  // start the cluster node
  val system = ActorSystem(systemName, config)
  println(s"running : ${system.name}")
}
