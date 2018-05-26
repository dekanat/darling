package darling

import java.io.{ByteArrayOutputStream, InputStream, InputStreamReader}
import java.util
import java.util.Properties
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, Session}

import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Base64
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.{Gmail, GmailScopes}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

/**
  * Created by spectrum on 5/14/2018.
  */


final class InvalidPayloadException(val msg: String) extends Throwable

// This is the mail builder despite the name
final case class MailPayload(var from: String = "",
                       var to: String = "",
                       var subject: String = "",
                       var message: String = "",
                       var cc: List[String] = List[String](),
                       var bcc: List[String] = List[String]())

final class Mail {

  import Mail._

  val payload = MailPayload()

  def from(from: String) = {
    payload.from = from
    this
  }

  def to(to: String) = {
    payload.to = to
    this
  }

  def andSubject(subject: String) = withSubject(subject)
  def withSubject(subject: String) = {
    payload.subject = subject
    this
  }

  def andMessage(message: String) = withMessage(message)
  def withMessage(message: String) = {
    payload.message = message
    this
  }

  def CC(cc: List[String]) = {
    payload.cc = cc
    this
  }

  def BCC(bcc: List[String]) = {
    payload.bcc = bcc
    this
  }

  @throws(classOf[InvalidPayloadException])
  def darling: Unit = {
    if (isPayloadValid) {
      val email = createEmail(payload)
      sendMessage(service, "me", email)
    } else {
      throw new InvalidPayloadException(getValidationError)
    }
  }

  private def isPayloadValid = payload.from.nonEmpty && payload.to.nonEmpty

  private def getValidationError = {
    val stringBuilder = StringBuilder.newBuilder

    if (payload.from.isEmpty) stringBuilder.append(s"Invalid sender: ${payload.from}")
    if (payload.to.isEmpty) stringBuilder.append(s"Invalid recipient: ${payload.to}")

    stringBuilder toString
  }
}

private object Mail {

  import javax.mail.Message._

  val conf = ConfigFactory.load()

  private val jsonFactory = JacksonFactory.getDefaultInstance()

  private val credentialsFolder = conf.getString("darling.credentials-folder")
  private val clientSecretFile = conf.getString("darling.client-secret")
  private val appName = conf.getString("darling.app-name")
  private val gmailAccessType = conf.getString("darling.access-type")
  private val gmailUser = conf.getString("darling.gamil-user")

  private val scopes = new util.ArrayList[String]()
  scopes.add(GmailScopes.GMAIL_COMPOSE)
  scopes.add(GmailScopes.GMAIL_SEND)

  private lazy val clientId: InputStream = getClass.getResourceAsStream(clientSecretFile)
  private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

  private val service = new Gmail.Builder(httpTransport, jsonFactory, getCredentials)
    .setApplicationName(appName)
    .build()

  private val logger = Logger[Mail]

  private def getCredentials = {
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(clientId))

    val flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, scopes)
      .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(credentialsFolder)))
      .setAccessType(gmailAccessType)
      .build()

    new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(gmailUser)
  }

  private def createEmail(payload: MailPayload) = {
    val props = new Properties()
    val session = Session.getDefaultInstance(props, null)

    val email = new MimeMessage(session)

    email.setFrom(new InternetAddress(payload.from))
    email.addRecipient(RecipientType.TO, new InternetAddress(payload.to))

    if (payload.cc.nonEmpty) {
      val ccAddresses = payload.cc.map({
        new InternetAddress(_)
      }).toArray[Address]

      email.setRecipients(RecipientType.CC, ccAddresses)
    }

    if (payload.bcc.nonEmpty) {
      val bccAddresses = payload.bcc.map({
        new InternetAddress(_)
      }).toArray[Address]

      email.setRecipients(RecipientType.BCC, bccAddresses)
    }

    email.setSubject(payload.subject)
    email.setText(payload.message)

    email
  }

  private def createMessageWithEmail(emailContent: MimeMessage) = {
    val buffer = new ByteArrayOutputStream()
    emailContent.writeTo(buffer)
    val bytes = buffer.toByteArray()
    val encodedEmail = Base64.encodeBase64URLSafeString(bytes)
    val message = new Message()
    message.setRaw(encodedEmail)
    message
  }

  private def sendMessage(service: Gmail, userId: String, emailContent: MimeMessage) = {
    var message = createMessageWithEmail(emailContent)
    message = service.users().messages().send(userId, message).execute()

    logger.info(s"Message id: ${message.getId}")
  }
}

