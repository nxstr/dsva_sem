# DSVA Semestrální práce
## Deadlock detection - Chandy Misra Hass - Java JMS
Distribuovaná aplikace, která provádí detekci zablokování na komunikace. Aplikace využívá technologii JMS a broker ActiveMQ pro zasílání zpráv mezi uzly.
Program implementuje Chandy-Misra-Hass algoritmus pro detekci zablokování.

## Spuštění
### ActiveMQ
Aplikace použiva dvě instance brokerů ActiveMQ verze 5.11.0. Brokeři musí běžet na dvou různých virtuálních strojích, a je nutné upravit jejich výchozí konfiguraci, aby se mohli navzájem propojit.

Ve složce ```activemq``` v souboru ```../conf/activemq.xml``` je potřeba nastavit unikátní jméno pro každý broker (položka ```brokerName```):
```
<broker xmlns="http://activemq.apache.org/schema/core" brokerName="broker1" dataDirectory="${activemq.data}">
```
Následně je třeba uvnitř bloku ```<broker></broker>``` přidat sekci, která obsahuje IP adresu jednoho brokeru, ke kterému se připojí druhý:
```
<networkConnectors>
    <networkConnector name="bridge" uri="static:(tcp://<ip addresa brokera>:61616)" duplex="false"/>
</networkConnectors>
```

Toto nastavení je nutné provést pro každou instanci brokeru.

Aplikace lze spustit i s jedním brokerem, případně můžete během používání aplikace jeden broker zastavit.

### Uzly
Aplikaci lze zkompilovat pomocí příkazu (vyžaduje nainstalovaný Maven):
```
mvn clean package
```
Pro zjednodušení je v repozitáři k dispozici již zkompilovaný ```.jar``` soubor (ve složce ```target```).

Každý uzel lze spustit na samostatném virtuálním stroji. Uzly nepoužívají IP adresy, protože díky brokeru komunikují pouze prostřednictvím jednotlivých topiků. Porty jsou přiděleny pouze pro použití příkazů ```cURL```. Je však nutné označit uzly unikátními číselnými identifikátory.

Každý uzel má tři povinné parametry a jeden volitelný. Příkaz pro spuštění:
```
java -cp target/dsva_sem-1.0-SNAPSHOT.jar Node <id> <ip prvniho brokeru> <ip druheho brokeru> <delay>
```
- id - unikátní identifikátor uzlu (integer).
- ip brokerů - parametry ve formátu ```0.0.0.0```.
- delay (volitelný) - simuluje zpoždění v kanálu, hodnota je v milisekundách. Pokud parametr není zadán, žádné zpoždění v rámci uzlu nebude.

## Příkazy
Aplikace podporuje příkazy prostřednictvím cURL a konzolových příkazů. cURL příkazy mají formát: ```cURL X <TYP> http://localhost:<port uzlu>/<příkaz>```
- TYP - GET nebo POST.
- port uzlu - Unikátní port pro každý uzel, který odpovídá hodnotě ```5000+id uzlu```.
- příkaz - Jednotlivý příkaz, jejich seznam viz níže.

### Seznam dostupných příkazů
- ```get_dependencies``` (konzolový příkaz: ```-ld```) - Ukazuje, na kterých uzlech je daný uzel závislý.
- ```get_type``` - Vrací typ uzlu (ACTIVE nebo PASSIVE).
- ```get_state``` - Vrací stav uzlu, slouží pro interní účely běhu aplikace, například pro určení typu požadavku v kritické sekci (PROVOKING, CREATE_SINGLE_DEPEND, ANSWER_SINGLE, DETECTING, IDLE).
- ```get_count``` - Vrací počet aktivních uzlů v systému.
- ```provoke_full_deadlock``` (konzolový příkaz: ```-pdf```) - Spustí proces blokování uzlů. Každý uzel náhodně vybírá z množiny známých uzlů a posílá jim požadavky, čímž se na ně stává závislý a formuje svou množinu závislostí. Každý uzel může být závislý na 0–2 jiných uzlech.
- ```provoke_single/{receiverId}``` (konzolový příkaz: ```-pds {receiverId}```) - Provádí jednorázový požadavek způsobující závislost. Lze tak vytvořit vlastní topologie zablokování.
- ```answer_single/{receiverId}``` (konzolový příkaz: ```-as {receiverId}```) - Posílá "odpověď" na požadavek způsobující závislost. Tato zpráva odstraní závislost uzlu, kterému byla odeslána.
- ```detect_deadlock``` (konzolový příkaz: ```-dd```) - Spustí proces detekce zablokování. Proces může být spuštěn z libovolného uzlu. Pokud zablokování existuje, iniciátor o něm informuje všechny uzly.
- ```clear``` (konzolový příkaz: ```-cd```) - Smaže všechny závislosti a vrátí uzly do počátečního stavu.
- ```set_active``` - Nastaví typ uzlu na ACTIVE, pokud je to možné.
- ```set_passive``` - Nastaví typ uzlu na PASSIVE.
- ```exit``` (konzolový příkaz: ```-out```) - Vypne uzel. Před vypnutím uzel všem oznámí, že se odpojuje.

## Procesy aplikace
### Detekce zablokování
Pro detekci je použit Chandy-Misra-Hass algoritmus (pseudokód z přednášek). Pro účely aplikace byl vybrán typ zablokování založený na komunikaci.
### Kritická sekce
Pro spuštění procesu detekce zablokování je nutné vytvořit "simulaci" zablokování (hlavně randomizovaný provoke_full_deadlock).
Při vytváření zablokování uzel vstupuje do kritické sekce, aby zabránil vzniku cyklu např. mezi pouze dvěma uzly kvůli souběžným požadavkům. V kritické sekci může uzel žádat jen ty uzly, které na něj nejsou závislé.
Samotná detekce kritickou sekci nevyužívá, protože tento proces nemění stavy uzlů ani neovlivňuje kritická data.
### JMS
Aplikace využívá čtyři topiky pro komunikaci, dva veřejné (broadcast a heartbeat) a Dva s využitím selektorů (direct a detectDirect) pro "osobní" komunikaci. Selektory slouží k identifikaci uzlů podle jejich ID. Každý topik má svého listenera, který přijímá a zpracovává zprávy.
### Heartbeat
Heartbeat není součástí základního algoritmu detekce zablokování, ale v aplikaci plní dvě funkce:
- Detekce pádu uzlu: Pokud během určitého časového intervalu uzel nepřijme heartbeat zprávy, považuje uzel za neaktivní (spadlý) a odstraní ho z dat (upraví počet uzlů, možné závislosti, případně odstraní aktivní závislost apod.).
- Detekce nepřítomnosti zablokování: Pokud uzel není v deadlocku, ale provádí detekci, díky absenci odpovědí od jiných uzlů (ale za přítomnosti jejich heartbeatů) může rozhodnout, že nedošlo k zablokování.
### Paměť uzlu
Uzly nemají pevně definované "sousedy" ani konkrétní topologii. Komunikují prostřednictvím brokeru a mají jednoznačné identifikátory.
Po probuzení každý uzel vysílá Join zprávu, protože potřebuje znát počet aktivních uzlů a jejich ID. Tyto informace jsou nezbytné pro:
- Generování zablokování (výběr uzlů pro posílání zpráv a vytvoření závislostí).
- Souhlas s vstupem do kritické sekce.
- Proces detekce (generování seznamů Last, Wait, Parent a Number).

## Logy
Uzly generují logy během svého provozu. Tyto logy se vypisují do konzole a do souboru ```app.log```.
