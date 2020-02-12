package phobosive.reservation.impl.repository

import java.time.Instant

import akka.Done
import phobosive.reservation.api.{CustomerReservationReport, ReservationStatus}
import slick.dbio.DBIO
import slick.lifted.ProvenShape
//import com.github.tminglei.slickpg.ExPostgresProfile
//import CommonPostgresProfile.api._
//import pl.iterators.kebs._
//import enums._
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.ExecutionContext.Implicits.global

class ReservationReportRepository(database: Database) {

  class CustomerReservationReportTable(tag: Tag) extends Table[CustomerReservationReport](tag, "customer_reservation_report") {
    def reservationId: Rep[String]        = column[String]("reservation_id", O.PrimaryKey)
    def eventId: Rep[String]              = column[String]("event_id")
    def customerId: Rep[String]           = column[String]("customer_id")
    def ordered: Rep[Option[Int]]         = column[Option[Int]]("ordered")
    def reserved: Rep[Int]                = column[Int]("reserved")
    def status: Rep[String]               = column[String]("status")
    def reservedAt: Rep[Instant]          = column[Instant]("reserved_at")
    def cancelledAt: Rep[Option[Instant]] = column[Option[Instant]]("cancelled_at")

    def idx_eventId_customerId = index("idx__customer_reservation_report__event_id__customer_id", (eventId, customerId))
    def idx_eventId            = index("idx__customer_reservation_report__event_id", eventId)
    def idx_customerId         = index("idx__customer_reservation_report__customer_id", customerId)
    def idx_status             = index("idx__customer_reservation_report__status", status)

    override def * : ProvenShape[CustomerReservationReport] =
      (reservationId, eventId, customerId, ordered, reserved, status, reservedAt, cancelledAt) <> ((CustomerReservationReport.apply _).tupled, CustomerReservationReport.unapply)
  }

  val reservationReportTable = TableQuery[CustomerReservationReportTable]

  def createTable() = reservationReportTable.schema.createIfNotExists

  def findAllNotCancelled(): DBIOAction[Seq[CustomerReservationReport], Streaming[CustomerReservationReport], Effect.Read] =
    reservationReportTable
      .filterNot(r => r.status === ReservationStatus.Cancelled.entryName) // todo enum cant be queried... kebsEnum should solve it but didnt
      .result
  def findAllEventId(): DBIOAction[Seq[String], NoStream, Effect.Read] = reservationReportTable.map(_.eventId).distinct.result
  def findByReservationId(reservationId: String): DBIOAction[Option[CustomerReservationReport], NoStream, Effect.Read] =
    findByReservationIdCompiled(reservationId).result.headOption
  def findByEventId(eventId: String): DBIOAction[Seq[CustomerReservationReport], NoStream, Effect.Read]       = findByEventIdCompiled(eventId).result
  def findByCustomerId(customerId: String): DBIOAction[Seq[CustomerReservationReport], NoStream, Effect.Read] = findByCustomerIdCompiled(customerId).result
  def findByEventIdAndCustomerId(eventId: String, customerId: String): DBIOAction[Option[CustomerReservationReport], NoStream, Effect.Read] =
    findByEventIdAndCustomerIdCompiled(eventId, customerId).result.headOption

  def createReport(report: CustomerReservationReport): DBIO[Done] = {
    import report._
    findByEventIdAndCustomerId(eventId, customerId)
      .flatMap {
        case None =>
          reservationReportTable += report
        case _ => DBIO.successful(Done)
      }
      .map(_ => Done)
      .transactionally
  }

  def updateReservationTime(reservationId: String, reservedAt: Instant): DBIO[Done] =
    findByReservationIdToUpdateReservationAtCompiled(reservationId)
      .update(reservedAt)
      .map(_ => Done)
      .transactionally

  def updateCancelTime(reservationId: String, cancelledAt: Instant, status: ReservationStatus): DBIO[Done] =
    reservationReportTable
      .filter(_.reservationId === reservationId)
      .map(res => (res.cancelledAt, res.status))
      .update((Some(cancelledAt), status.entryName))
      .map(_ => Done)
      .transactionally

  private def findByEventIdAndCustomerIdQuery(eventId: Rep[String], customerId: Rep[String]) =
    reservationReportTable.filter(report => report.eventId === eventId && report.customerId === customerId)

  private def findByReservationIdToUpdateReservationAt(reservationId: Rep[String]) =
    reservationReportTable
      .filter(_.reservationId === reservationId)
      .map(_.reservedAt)

  private lazy val findByEventIdAndCustomerIdCompiled               = Compiled(findByEventIdAndCustomerIdQuery _)
  private lazy val findByReservationIdToUpdateReservationAtCompiled = Compiled(findByReservationIdToUpdateReservationAt _)
  private lazy val findByReservationIdCompiled                      = reservationReportTable.findBy(_.reservationId)
  private lazy val findByEventIdCompiled                            = reservationReportTable.findBy(_.eventId)
  private lazy val findByCustomerIdCompiled                         = reservationReportTable.findBy(_.customerId)
}
