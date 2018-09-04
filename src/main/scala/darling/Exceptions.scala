package darling

/**
  * Created by spectrum on Aug, 2018
  */
final class MailerNotConfigured(val msg: String) extends Throwable

final class InvalidPayloadException(val msg: String) extends Throwable

final class ClientNotFoundException(val msg: String) extends Throwable

final class UnsupportedGmailScopeException(val msg: String) extends Throwable

final class InvalidDarlingConfigException(val msg: String) extends Throwable
