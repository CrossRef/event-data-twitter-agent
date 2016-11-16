# Event Data Twitter Agent

Crossref Event Data Twitter agent. Connects to Gnip to monitor Twitter for events.

Under development September 2016.

Contains a snapshot of a [fork of the Twitter HoseBird Client](https://github.com/jimmoffitt/hbc) because Twitter's version isn't compatible with their own PowerTrack 2.0 service. Pending Twitter deploying HBC on Maven.

## Config

 - `:status-service-base`
 - `:status-service-auth-token`
 - `:evidence-service-auth-token`
 - `:evidence-service-base`
 - `:gnip-username`
 - `:gnip-password`
 - `:gnip-rules-url` e.g. "https://api.gnip.com/accounts/«user»/publishers/twitter/streams/track/prod/rules.json"
 - `:reverse-service-base`

