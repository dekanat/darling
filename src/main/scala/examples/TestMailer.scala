package examples

import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import darling.{DarlingConfiguration, Mail, send}
import javax.mail.internet.MimeMessage

/**
  * Created by spectrum on 5/26/2018.
  */
class TestMailer {
  def main(args: Array[String]) {
    Mail.configure(DarlingConfiguration(new LocalServerReceiver()))

    send a new Mail from "leia@gmail.com" to "obi_wan@wherever.io" withSubject "Help" andMessage "Help me, Obi-Wan Kenobi. You're my only hope." darling

    val mimeMsg = someOuterFunctionThatCreatesAMimeMessage
    send the mimeMsg darling
  }

  private def someOuterFunctionThatCreatesAMimeMessage: MimeMessage = ???
}
