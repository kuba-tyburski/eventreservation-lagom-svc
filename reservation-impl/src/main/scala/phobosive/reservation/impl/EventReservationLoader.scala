package phobosive.reservation.impl

import akka.actor.ActorSystem
import akka.cluster.sharding.typed.scaladsl.Entity
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import phobosive.reservation.api.EventReservationService
import phobosive.reservation.impl.repository.ReservationReportRepository
import play.api.db.HikariCPComponents
import play.api.libs.ws.ahc.AhcWSComponents

import scala.concurrent.ExecutionContext

class ReservationLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new ReservationApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ReservationApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[EventReservationService])
}

trait EventReservationComponents extends LagomServerComponents with SlickPersistenceComponents with HikariCPComponents with AhcWSComponents {
  implicit def executionContext: ExecutionContext
  implicit val system = ActorSystem("TestSystem")

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[EventReservationService](wire[EventReservationServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = ReservationSerializerRegistry

  lazy val reportRepository: ReservationReportRepository =
    wire[ReservationReportRepository]
  readSide.register(wire[EventReservationReportProcessor])

  // Initialize the sharding of the Aggregate. The following starts the aggregate Behavior under
  // a given sharding entity typeKey.
  clusterSharding.init(
    Entity(EventReservation.typeKey)(
      entityContext => EventReservation(entityContext)
    )
  )
}

abstract class ReservationApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with EventReservationComponents
    with LagomKafkaComponents {}
