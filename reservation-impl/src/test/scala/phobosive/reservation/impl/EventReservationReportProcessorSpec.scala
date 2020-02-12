package phobosive.reservation.impl

import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.persistence.query.Sequence
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server.LagomApplication
import com.lightbend.lagom.scaladsl.testkit.{ReadSideTestDriver, ServiceTest, TestTopicComponents}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import phobosive.reservation.api.ReservationStatus
import phobosive.reservation.impl.model.ReservationEvent.{Event, ReservationAdded, ReservationCancelled, ReservationExtended}

import scala.concurrent.{ExecutionContext, Future}

class EventReservationReportProcessorSpec extends AnyWordSpec with BeforeAndAfterAll with Matchers with ScalaFutures with OptionValues {

  private val server = ServiceTest.startServer(ServiceTest.defaultSetup.withJdbc(true)) { ctx =>
    new LagomApplication(ctx) with EventReservationComponents with TestTopicComponents {
      override def serviceLocator: ServiceLocator    = NoServiceLocator
      override lazy val readSide: ReadSideTestDriver = new ReadSideTestDriver()(materializer, executionContext)
    }
  }

  override def afterAll(): Unit = server.stop()

  private val testDriver                    = server.application.readSide
  private val reportRepository              = server.application.reportRepository
  private val offset                        = new AtomicInteger()
  private val db                            = server.application.db
  private implicit val ec: ExecutionContext = server.actorSystem.dispatcher

  "EventReservationReportProcessor" should {

    "create a report on ReservationAdded event" in {
      // given
      val expectedEventId       = UUID.randomUUID().toString
      val expectedCustomerId    = UUID.randomUUID().toString
      val expectedReservationId = UUID.randomUUID().toString
      val expectedReservedAt    = Instant.now()

      db.run(reportRepository.findByEventId(expectedEventId)).futureValue should equal(Nil)

      // when
      val reportF =
        for {
          _         <- feedEvent(expectedEventId, ReservationAdded(expectedCustomerId, expectedReservationId, 3, 3, expectedReservedAt, ReservationStatus.Full))
          reportOpt <- db.run(reportRepository.findByReservationId(expectedReservationId))
        } yield reportOpt

      // then
      whenReady(reportF) { reportOpt =>
        reportOpt shouldBe defined
        reportOpt.value.id should equal(expectedReservationId)
        reportOpt.value.customerId should equal(expectedCustomerId)
        reportOpt.value.eventId should equal(expectedEventId)
        reportOpt.value.cancelledAt shouldNot be(defined)
      }
    }

    "update reservedAt on ReservationExtended event" in {
      // given
      val expectedEventId       = UUID.randomUUID().toString
      val expectedCustomerId    = UUID.randomUUID().toString
      val expectedReservationId = UUID.randomUUID().toString
      val reservedAt            = Instant.now()

      db.run(reportRepository.findByEventId(expectedEventId)).futureValue should equal(Nil)

      val reportF =
        for {
          _         <- feedEvent(expectedEventId, ReservationAdded(expectedCustomerId, expectedReservationId, 3, 3, reservedAt, ReservationStatus.Full))
          reportOpt <- db.run(reportRepository.findByReservationId(expectedReservationId))
        } yield reportOpt

      whenReady(reportF) { reportOpt =>
        reportOpt shouldBe defined
      }

      // when
      val expectedReservedAt = reservedAt.plusSeconds(5)
      val extendReportF =
        for {
          _         <- feedEvent(expectedEventId, ReservationExtended(expectedCustomerId, expectedReservationId, expectedReservedAt))
          reportOpt <- db.run(reportRepository.findByReservationId(expectedReservationId))
        } yield reportOpt

      // then
      whenReady(extendReportF) { reportOpt =>
        reportOpt shouldBe defined
        reportOpt.value.reservedAt should equal(expectedReservedAt)
      }
    }

    "set Cancelled status and cancelledAt on ReservationCancelled event" in {
      // given
      val expectedEventId       = UUID.randomUUID().toString
      val expectedCustomerId    = UUID.randomUUID().toString
      val expectedReservationId = UUID.randomUUID().toString
      val reservedAt            = Instant.now()

      db.run(reportRepository.findByEventId(expectedEventId)).futureValue should equal(Nil)

      val reportF =
        for {
          _         <- feedEvent(expectedEventId, ReservationAdded(expectedCustomerId, expectedReservationId, 3, 3, reservedAt, ReservationStatus.Full))
          reportOpt <- db.run(reportRepository.findByReservationId(expectedReservationId))
        } yield reportOpt

      whenReady(reportF) { reportOpt =>
        reportOpt shouldBe defined
      }

      // when
      val expectedCancelledAt = reservedAt.plusSeconds(5)
      val extendReportF =
        for {
          _         <- feedEvent(expectedEventId, ReservationCancelled(expectedCustomerId, expectedReservationId, expectedCancelledAt))
          reportOpt <- db.run(reportRepository.findByReservationId(expectedReservationId))
        } yield reportOpt

      // then
      whenReady(extendReportF) { reportOpt =>
        reportOpt shouldBe defined
        reportOpt.value.cancelledAt shouldBe Some(expectedCancelledAt)
        reportOpt.value.status should equal(ReservationStatus.Cancelled.entryName)
        reportOpt.value.reservedAt should equal(reservedAt)
      }
    }
  }

  private def feedEvent(eventId: String, event: Event): Future[Done] =
    testDriver.feed(eventId, event, Sequence(offset.getAndIncrement))
}
