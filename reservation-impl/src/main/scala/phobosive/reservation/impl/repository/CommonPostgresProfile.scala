package phobosive.reservation.impl.repository

import com.github.tminglei.slickpg.ExPostgresProfile
import pl.iterators.kebs.Kebs
import pl.iterators.kebs.enums.KebsEnums

object CommonPostgresProfile extends ExPostgresProfile {
  override val api: API = new API {}
  trait API extends super.API with Kebs with KebsEnums.Lowercase
}
