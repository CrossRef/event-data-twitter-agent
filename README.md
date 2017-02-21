# Event Data Twitter Agent

<img src="doc/logo.png" align="right" style="float: right">

Crossref Event Data Twitter agent. Connects to Gnip to monitor Twitter for events. Uses the Crossref Event Data Agent Framework.

## To run

To run as an agent, `lein run`. To update the rules in Gnip, which should be one when the domain list artifact is updated, `lein run update-rules`.

## Tests

    time docker-compose run -w /usr/src/app test lein test :unit

## Demo

    time docker-compose -f docker-compose-demo.yml run -w /usr/src/app test lein repl

## Config

 - `GNIP_RULES_URL` -  e.g. "https://api.gnip.com/accounts/«user»/publishers/twitter/streams/track/prod/rules.json"
 - `POWERTRACK_ENDPOINT` - including any arguments, e.g. "https://gnip-stream.twitter.com/stream/powertrack/accounts/«account»/publishers/twitter/prod.json?backfillMinutes=5"
 - `PERCOLATOR_URL_BASE` e.g. https://percolator.eventdata.crossref.org
 - `JWT_TOKEN`
 - `STATUS_SERVICE_BASE`
 - `GNIP_USERNAME`
 - `GNIP_PASSWORD`
 - `ARTIFACT_BASE`, e.g. https://artifact.eventdata.crossref.org
