# Darling
[![Build Status](https://travis-ci.org/dekanat/darling.svg?branch=master)](https://travis-ci.org/dekanat/darling)


Darling is a [fluent](https://martinfowler.com/bliki/FluentInterface.html) GMail API implemented in Scala

# Vocabulary

Darling is a DSL around GMail API and supports the following verbs
  * `send`
  * `reply`
  * `draft`
  
Every sentence starts with a verb and ends with `darling`

# Configuration

Darling needs to be configured. It has two configurable parameters
  * Google OAuth2.0 verification code receiver
  * GMail access permissions

Currently configuration must be done both programmatically and via a conf file.

In order to authenticate with Google API, a verification code receiver should be provided to Darling. 
This is done from code via `DarlingConfiguration` object and looks like this:
```scala
Darling.configure(DarlingConfiguration(new LocalServerReceiver()))
``` 
Custom verification code receivers could be created by implementing Google OAuth2 `VerificationCodeReceiver`.

The rest of the configuration is in the config file and it looks like so:

```hocon
// Folder where the credentials for gmail clients will be saved
darling.credentials-folder = "credentials"
// Your app name
darling.app-name = "darling"
// GMail API acceess type
darling.access-type = "offline"

//GMail clients. Darling supports multiple (GMail) accounts
darling.clients = [
  {
    // Client name  
    name = "leia"
    // Clients secret
    secret = "leia_secret.json"
    scopes = [
      "MAIL_GOOGLE_COM",
      "GMAIL_COMPOSE",
      "GMAIL_SEND"
    ]
  },
  {
    name = "luke"
    secret = "luke.json"
    scopes = [
      "MAIL_GOOGLE_COM",
      "GMAIL_COMPOSE",
      "GMAIL_INSERT",
      "GMAIL_LABELS",
      "GMAIL_METADATA",
      "GMAIL_MODIFY",
      "GMAIL_READONLY",
      "GMAIL_SEND"
    ]
  }
]
```

# Usage

```Scala
package examples

import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import darling.{DarlingConfiguration, Mail, send}
import javax.mail.internet.MimeMessage

class TestMailer {
  def main(args: Array[String]) {
    Mail.configure(DarlingConfiguration(new LocalServerReceiver()))

    
    send a new Mail from "leia@gmail.com" to "obi_wan@wherever.io" withSubject "Help" andMessage "Help me, Obi-Wan Kenobi. You're my only hope." darling

    val mimeMsg = someOuterFunctionThatCreatesAMimeMessage
    send the mimeMsg darling
  }

  private def someOuterFunctionThatCreatesAMimeMessage: MimeMessage = ???
}
```
