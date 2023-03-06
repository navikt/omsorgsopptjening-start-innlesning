# omsorgsopptjening-start-innlesning
Kaller BA system som begynner opplasting av barnetrygd for år til topic

## Arkitektur
[Overordnet arkitektur omsorgsopptjening](https://confluence.adeo.no/x/Gl_qHg)

## Test miljø:
### Kall tjenesten i tesmiljø:
1) Gå til endepunktet du ønsker å kalle.
2) Logg inn med trygdeetaten mailen din. 
3) Om du ikke får logget inn: sørg for at du er medlem av gruppen pensjonopptjeningtest i azureAd test tennant(trygdeetaten.no)
2) Applikasjonen skal etter innloggin autmoatisk videresende deg til urlen du skrev inn.

## Lokalt oppsett
Start docker daemon eller colima

## Colima trouble?
1) Setting the DOCKER_HOST environment variable to point to Colima socket.
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"

2) Linking the Colima socket to the default socket path. Note that this may break other Docker servers.
sudo ln -sf $HOME/.colima/default/docker.sock /var/run/docker.sock
3) Linking colima test container socket
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
