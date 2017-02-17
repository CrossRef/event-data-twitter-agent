# Event Data Twitter Agent

<img src="doc/logo.png" align="right" style="float: right">

Crossref Event Data Twitter agent. Connects to Gnip to monitor Twitter for events. Uses the Crossref Event Data Agent Framework.

Contains a snapshot of a [fork of the Twitter HoseBird Client](https://github.com/jimmoffitt/hbc) because Twitter's version isn't compatible with their own PowerTrack 2.0 service. Pending Twitter deploying HBC on Maven.

## To run

To run as an agent, `lein run`. To update the rules in Gnip, which should be one when the domain list artifact is updated, `lein run update-rules`.

## Tests

    time docker-compose -f docker-compose-unit-tests.yml run -w /usr/src/app test lein test :unit

## Demo

    time docker-compose -f docker-compose.yml run -w /usr/src/app test lein repl

## Config

 - `GNIP_RULES_URL` -  e.g. "https://api.gnip.com/accounts/«user»/publishers/twitter/streams/track/prod/rules.json"
 - `POWERTRACK_ENDPOINT` - NB: The hostname should not be included. e.g. "/stream/powertrack/accounts/«user»/publishers/twitter/prod.json"
 - `PERCOLATOR_URL_BASE` e.g. https://percolator.eventdata.crossref.org
 - `JWT_TOKEN`
 - `STATUS_SERVICE_BASE`
 - `GNIP_USERNAME`
 - `GNIP_PASSWORD`
 - `ARTIFACT_BASE`, e.g. https://artifact.eventdata.crossref.org
