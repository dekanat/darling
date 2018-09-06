package darling

import java.io.{ByteArrayOutputStream, InputStream, InputStreamReader}
import java.util.Properties

import com.google.api.client.extensions.java6.auth.oauth2.{AuthorizationCodeInstalledApp, VerificationCodeReceiver}
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow, GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.Base64
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import javax.mail.internet.{InternetAddress, MimeMessage}
import javax.mail.{Address, MessagingException, Session}

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * Created by spectrum on 5/14/2018.
  */
// This is the mail builder despite the name
final case class MailPayload(var from: String = "",
                             var to: String = "",
                             var subject: String = "",
                             var message: String = "",
                             var cc: List[String] = Nil,
                             var bcc: List[String] = Nil)

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
  @throws(classOf[MailerNotConfigured])
  def darling(implicit conf: DarlingConfiguration): Unit = {
    if (!Mail.isConfigured)
      throw new MailerNotConfigured("Mailer not configured!")

    if (isPayloadValid) {
      val gmailUser = payload.from.takeWhile(_ != '@')
      val service = {
        Mail.services.get(gmailUser) match {
          case Some(s) => s
          case None => {
            val s = new Gmail.Builder(httpTransport, jsonFactory, Mail.getCredentials(gmailUser))
              .setApplicationName(appName)
              .build()

            Mail.services.put(gmailUser, s)
            s
          }
        }
      }

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

case class DarlingConfiguration(verificationCodeReceiver: VerificationCodeReceiver) {
  def isValid: Boolean = verificationCodeReceiver != null
}

@throws(classOf[InvalidDarlingConfigException])
object Mail {

  import javax.mail.Message._

  private val conf = ConfigFactory.load()

  private val jsonFactory = JacksonFactory.getDefaultInstance

  private val services = mutable.Map[String, Gmail]()

  private val credentialsFolder = {
    val confPath = "darling.credentials-folder"

    if (conf.hasPathOrNull(confPath)) {
      if (conf.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("Credential folder path cannot be null")
      } else {
        conf.getString(confPath)
      }
    } else {
      throw new InvalidDarlingConfigException(s"$confPath key is missing from configs")
    }
  }

  private val appName = {
    val confPath = "darling.app-name"

    if (conf.hasPathOrNull(confPath)) {
      if (conf.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("App name cannot be null")
      } else {
        conf.getString(confPath)
      }
    } else {
      throw new InvalidDarlingConfigException(s"$confPath key is missing from configs")
    }
  }

  private val gmailAccessType = {
    val confPath = "darling.access-type"

    if (conf.hasPathOrNull(confPath)) {
      if (conf.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("GMail API Access type name cannot be null")
      } else {
        conf.getString(confPath)
      }
    } else {
      throw new InvalidDarlingConfigException(s"$confPath key is missing from configs")
    }
  }

  private val clients = {
    val confPath = "darling.clients"

    if (conf.hasPathOrNull(confPath)) {
      if (conf.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("GMail API clients parameter cannot be null")
      } else {
        val clientsRaw = conf.getConfigList(confPath)
        clientsRaw.asScala map {
          clientRaw => {
            val client = new DarlingClient(clientRaw)
            (client.name, client)
          }
        } toMap
      }
    } else {
      throw new InvalidDarlingConfigException(s"$confPath key is missing from configs")
    }
  }

  private var darlingConf: DarlingConfiguration = _

  private val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

  private val logger = Logger[Mail]

  def configure(conf: DarlingConfiguration): Unit = darlingConf = conf

  private def isConfigured = darlingConf != null && darlingConf.isValid

  @throws(classOf[UnsupportedGmailScopeException])
  private def getCredentials(gmailUser: String) = {
    val client = clients(gmailUser)

    if (client == null)
      throw new ClientNotFoundException(s"Client $gmailUser not found in configs")

    val clientId: InputStream = getClass.getResourceAsStream(s"/${client.secret}")
    val clientSecrets = GoogleClientSecrets.load(jsonFactory, new InputStreamReader(clientId))

    val flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, jsonFactory, clientSecrets, client.scopes.asJava)
      .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(credentialsFolder)))
      .setAccessType(gmailAccessType)
      .build()

    new AuthorizationCodeInstalledApp(flow, darlingConf.verificationCodeReceiver).authorize(gmailUser)
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
    val bytes = buffer.toByteArray
    val encodedEmail = Base64.encodeBase64URLSafeString(bytes)
    val message = new Message()
    message.setRaw(encodedEmail)
    message
  }

  private def sendMessage(service: Gmail, userId: String, emailContent: MimeMessage): Unit = {
    var message = createMessageWithEmail(emailContent)
    message = service.users().messages().send(userId, message).execute()

    logger.info(s"Message id: ${message.getId}")
  }
}

