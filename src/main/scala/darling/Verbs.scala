package darling

import javax.mail.internet.MimeMessage

/**
  * Created by spectrum on 5/26/2018.
  */

object send {
  def a(mail: Mail) = new Mail
  def the(mail: Mail) = mail
  def the(mail: MimeMessage) = new Mail
}

object reply {
  def to = new Mail
}

object draft {
  def a(mail: Mail) = new Mail
}
