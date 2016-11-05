[![Build Status](https://travis-ci.org/ouven/akka-k8s-seednode.svg?branch=master)](https://travis-ci.org/ouven/akka-k8s-seednode)
[![Maven Central](https://img.shields.io/maven-central/v/de.aktey.akka.k8s/seednode-config_2.11.svg?maxAge=2592000)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22seednode-config_2.11%22)

# Akka kubernetes seednode config
The purpose of this project is to provide an easy way to run akka
cluster applications on kubernetes.

Current version: 1.0.1

build.sbt
```sbt
libraryDependencies += "de.aktey.akka.k8s" %% "seednode-config" % "1.0.1"
``` 

## The problem
You start your application on a dynamically assigned node. So you cannot know
the seed nodes IP addresses, which needs to be configured at startup for akka
cluster.

## Solution
Kubernetes offers an API to look up the IP of a service. The endpoint of the API
can be derived from environment variables, set by kubernetes.

`de.aktey.akka.k8s.SeednodeConfig` asks the API for its service IPs, takes the
first five elements and configures a `com.typesafe.config.Config` object for an
akka cluster. The concrete name spaces, that are set are:
- `akka.remote.netty.tcp.hostname`
- `akka.cluster.seed-nodes`

If no service IP is found (because no k8s service for a pod is started yet),
`de.aktey.akka.k8s.SeednodeConfig#getConfig` will throw an exception, so the
container can fail fast. The first container up, will see only one seed IP
(its own) and will assume itself the cluster leader.

## Architectural 2 cents
Akka doesn't seem to be designed for dynamic node assignment see
[auto-downing section](http://doc.akka.io/docs/akka/snapshot/java/cluster-usage.html#Auto-downing__DO_NOT_USE_).
So it seems to be a good advice to have a closer look at the warning box in that
chapter.

## Design goals
- As this is a base library, it should not introduce more any dependencies
([#nodependencies](https://index.scala-lang.org/search?q=nodependencies)). So
this library uses only dependencies, that are already there by its nature:
    - scala
    - typesafe config (introduced by akka)
- fail fast

## How to use
```scala
import akka.actor._
import com.typesafe.config.ConfigFactory
import de.aktey.akka.k8s.SeednodeConfig

object Main extends App {

  val systemName = "akka-k8s"
  // create the seed node config
  val kubeConfig = K8sSeednodeConfig.getConfig(systemName)
  // load the rest of the config
  // resolve the variables introduced by `K8sSeednodeConfig.getConfig`
  val config = kubeConfig.withFallback(ConfigFactory.load()).resolve()

  // start the cluster node
  val system = ActorSystem(systemName, config)
  println(s"running : ")
}
```

Have a look at the example project. You can use it right away after building it with `sbt docker:publishLocal`.
Then you can use the kubernetes definition files in `seednode-config-example/src/k8s` to run
the example.

```
akka-k8s-seednode> kubectl create -f seednode-config-example/src/k8s
replicationcontroller "akka-k8s-example" created
service "akka-k8s-example" created

akka-k8s-seednode> kubectl get svc
NAME               CLUSTER-IP   EXTERNAL-IP   PORT(S)             AGE
akka-k8s-example   10.0.0.121   <nodes>       8080/TCP,2552/TCP   8s
kubernetes         10.0.0.1     <none>        443/TCP             5d

akka-k8s-seednode> kubectl get rc
NAME               DESIRED   CURRENT   AGE
akka-k8s-example   1         1         18s

akka-k8s-seednode> kubectl get pod
NAME                     READY     STATUS    RESTARTS   AGE
akka-k8s-example-ztehd   1/1       Running   0          21s

akka-k8s-seednode> kubectl scale rc akka-k8s-example --replicas=3
replicationcontroller "akka-k8s-example" scaled

akka-k8s-seednode> kubectl get pod
NAME                     READY     STATUS    RESTARTS   AGE
akka-k8s-example-ge388   1/1       Running   0          2s
akka-k8s-example-q87dn   1/1       Running   0          2s
akka-k8s-example-ztehd   1/1       Running   0          31s

akka-k8s-seednode> kubectl logs akka-k8s-example-ztehd
[...]
08:37:19.222 [akka-k8s-akka.actor.default-dispatcher-18] INFO  a.cluster.Cluster(akka://akka-k8s) - Cluster Node [akka.tcp://akka-k8s@172.17.0.2:2552] - Node [akka.tcp://akka-k8s@172.17.0.2:2552] is JOINING, roles []
08:37:19.233 [akka-k8s-akka.actor.default-dispatcher-4] INFO  a.cluster.Cluster(akka://akka-k8s) - Cluster Node [akka.tcp://akka-k8s@172.17.0.2:2552] - Leader is moving node [akka.tcp://akka-k8s@172.17.0.2:2552] to [Up]
08:37:51.301 [akka-k8s-akka.actor.default-dispatcher-5] DEBUG akka.remote.Remoting - Associated [akka.tcp://akka-k8s@172.17.0.2:2552] <- [akka.tcp://akka-k8s@172.17.0.5:2552]
08:37:51.594 [akka-k8s-akka.actor.default-dispatcher-5] DEBUG akka.remote.Remoting - Associated [akka.tcp://akka-k8s@172.17.0.2:2552] <- [akka.tcp://akka-k8s@172.17.0.4:2552]
08:37:51.700 [akka-k8s-akka.actor.default-dispatcher-5] INFO  a.cluster.Cluster(akka://akka-k8s) - Cluster Node [akka.tcp://akka-k8s@172.17.0.2:2552] - Node [akka.tcp://akka-k8s@172.17.0.5:2552] is JOINING, roles []
08:37:51.859 [akka-k8s-akka.actor.default-dispatcher-3] INFO  a.cluster.Cluster(akka://akka-k8s) - Cluster Node [akka.tcp://akka-k8s@172.17.0.2:2552] - Node [akka.tcp://akka-k8s@172.17.0.4:2552] is JOINING, roles []
08:37:52.181 [akka-k8s-akka.actor.default-dispatcher-6] INFO  a.cluster.Cluster(akka://akka-k8s) - Cluster Node [akka.tcp://akka-k8s@172.17.0.2:2552] - Leader is moving node [akka.tcp://akka-k8s@172.17.0.4:2552] to [Up]
08:37:52.189 [akka-k8s-akka.actor.default-dispatcher-6] INFO  a.cluster.Cluster(akka://akka-k8s) - Cluster Node [akka.tcp://akka-k8s@172.17.0.2:2552] - Leader is moving node [akka.tcp://akka-k8s@172.17.0.5:2552] to [Up]
[...]
```
