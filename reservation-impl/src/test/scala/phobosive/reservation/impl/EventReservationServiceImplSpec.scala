package phobosive.reservation.impl

import java.util.UUID

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.testkit.{ReadSideTestDriver, ServiceTest, TestTopicComponents}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import phobosive.reservation.api.{EventReservationService, TicketReservationRequest}

import scala.concurrent.ExecutionContext

class EventReservationServiceImplSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalaFutures {

  private val server = ServiceTest.startServer(ServiceTest.defaultSetup.withJdbc(true)) { ctx =>
    new LagomApplication(ctx) with EventReservationComponents with TestTopicComponents {
      override def serviceLocator: ServiceLocator    = NoServiceLocator
      override lazy val readSide: ReadSideTestDriver = new ReadSideTestDriver()(materializer, executionContext)
    }
  }

  override def afterAll(): Unit = server.stop()

  private implicit val ec: ExecutionContext = server.actorSystem.dispatcher

  private lazy val client = server.serviceClient.implement[EventReservationService]

  "EventReservationService" when {
    "health check requested" should {
      "respond" in {
        client.healthCheck().invoke().map { response =>
          response should equal("OK")
        }
      }
    }
    "get all reservations requested" should {
      "retrieve no data" in {
        client.getAllReservations.invoke().map { response =>
          response.events should be(Nil)
        }
      }
      "retrieve all event reservation data" in {
        // given
        val expectedEventId    = UUID.randomUUID().toString
        val expectedCustomerId = UUID.randomUUID().toString
        val expectedQuantity   = 3

        // when
        client.reserveTicket(expectedEventId, expectedCustomerId).invoke(TicketReservationRequest(expectedQuantity)).map { _ =>
          client.getAllReservations.invoke().map { response =>
            // then
            response.events should have size 1
            val eventReservationView = response.events.head
            eventReservationView.id should equal(expectedEventId)
            eventReservationView.reservations should have size 1
            val (resultCustomer, resultReservation) = eventReservationView.reservations.head
            resultCustomer should equal(expectedCustomerId)
            resultReservation.quantity should equal(expectedQuantity)
          }
        }
      }
    }
  }
}
