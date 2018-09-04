package darling

import com.google.api.services.gmail.GmailScopes
import com.typesafe.config.Config

import scala.collection.JavaConverters._

/**
  * Created by spectrum on Aug, 2018
  */
final class DarlingClient(private[this] val clientRaw: Config) {
  val name: String = {
    val confPath = "name"

    if (clientRaw.hasPathOrNull(confPath)) {
      if (clientRaw.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("Client name cannot be null")
      } else {
        clientRaw.getString(confPath)
      }
    } else {
      throw new InvalidDarlingConfigException(s"darling.clients.$confPath key is missing from configs")
    }
  }

  val secret: String = {
    val confPath = "secret"

    if (clientRaw.hasPathOrNull(confPath)) {
      if (clientRaw.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("Client secret filename cannot be null")
      } else {
        clientRaw.getString(confPath)
      }
    } else {
      throw new InvalidDarlingConfigException(s"darling.clients.$confPath key is missing from configs")
    }
  }


  val scopes: Seq[String] = {
    val confPath = "scopes"

    if (clientRaw.hasPathOrNull(confPath)) {
      if (clientRaw.getIsNull(confPath)) {
        throw new InvalidDarlingConfigException("GMail API access scopes cannot be null")
      } else {
        val scopesRaw = clientRaw.asInstanceOf[Config].getStringList("scopes")
        if (scopesRaw.size() == 0)
          throw new InvalidDarlingConfigException("At least one GMail API access scope should be specified")

        scopesRaw.asScala map {
          case "MAIL_GOOGLE_COM" => GmailScopes.MAIL_GOOGLE_COM
          case "GMAIL_COMPOSE" => GmailScopes.GMAIL_COMPOSE
          case "GMAIL_INSERT" => GmailScopes.GMAIL_INSERT
          case "GMAIL_LABELS" => GmailScopes.GMAIL_LABELS
          case "GMAIL_METADATA" => GmailScopes.GMAIL_METADATA
          case "GMAIL_MODIFY" => GmailScopes.GMAIL_MODIFY
          case "GMAIL_READONLY" => GmailScopes.GMAIL_READONLY
          case "GMAIL_SEND" => GmailScopes.GMAIL_SEND
          case "GMAIL_SETTINGS_BASIC" => GmailScopes.GMAIL_SETTINGS_BASIC
          case "GMAIL_SETTINGS_SHARING" => GmailScopes.GMAIL_SETTINGS_SHARING
          case other => throw new UnsupportedGmailScopeException(s"Unsupported GMail scope $other")
        }
      }
    } else {
      throw new InvalidDarlingConfigException(s"darling.clients.$confPath key is missing from configs")
    }
  }
}
