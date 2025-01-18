# DSVA semestralni prace
## Deadlock detection - Chandy Misra Hass - Java JMS
Distribuovana aplikace ktera provadi detekce zablokovani na komunikace. Aplikace pouziva technologie JMS a broker ActiveMQ pro posilani zprav mezi uzly.
Program implementuje Chandy-Misra-Hass algoritmus pro detekce zablokovani.

## Spusteni
### ActiveMQ
Aplikace pouziva dve instance brokeru ActiveMQ verze 5.11.0. Brokery museji bezet na dvou ruznych virtualnich strojich, a je nutno zmenit jejich defaultni konfigurace aby mohli se navzajem pripojit.

Ve slozce activemq ve souboru ```../conf/activemq.xml``` je nutno nastavit unikatni jmeno pro kazdy broker (polozka ```brokerName```).
```
<broker xmlns="http://activemq.apache.org/schema/core" brokerName="broker1" dataDirectory="${activemq.data}">
```
Nasledne, uvnirt bloku ```<broker></broker>``` je potreba pridat block, ktery bude obsahovat ip adresu jednoho brokeru, ke kteremu se pripoji druhy.
```
<networkConnectors>
    <networkConnector name="bridge" uri="static:(tcp://<ip addresa brokera>:61616)" duplex="false"/>
</networkConnectors>
```

Pro kazdou instanci brokeru je potreba provest toto nastaveni.

Aplikace se da spustit i s jednim brokerem, nebo jeden muzete "zabit" behem pouziti aplikace.

### Uzly
Aplikace se za zkompilovat prikazem (vyzaduje nainstalovany maven):
```
mvn clean package
```
Pro zjednoduseni, v repozitari se nachazi zkompilovany .jar soubor.

Kazdy uzel se da spustit v jednotlivem virtualnim stroji. Uzly nepouzivaji ip adresy, protoze diky brokeru pouzivaji jenom jednotliva topiky, pomoci kterych komunikuji. 
Porty jsou prirazene jenom pro pouziti cURL prikazu. Nicmene, uzly je nutno oznacit unikatnimi int identifikatory.
Kazdy uzel ma 3 pozadovane parametry a jeden volitelny. Prikaz pro spusteni:
```
java -cp target/dsva_sem-1.0-SNAPSHOT.jar Node <id> <ip prvniho brokeru> <ip druheho brokeru> <delay>
```
- id - unikatni identifikator uzlu, integer.
- ip brokeru - parametr ve formatu ```0.0.0.0```.
- delay - volitelny parametr, simulujici zpozdeni v kanalu. hodnota je v milisekundach. Pokud parametr neni nastaven, zadne zpozdeni v ramci jednotliveho uzlu nebude.

## Prikazy
Aplikace ma cURL a konzolove prikazy. cURL jsou ve formatu ```cURL X <TYP> http://localhost:<port uzlu>/<prikaz>```
- TYP - GET nebo POST.
- port uzlu - je to unikatni port pro kazdy uzel, je vzdy dan hodnotou 5000+id uzlu.
- prikaz - jednotlivy prikaz, seznam viz dale.

### Seznam dostupnych prikazu
- ```get_dependencies``` (prislusny konzolovy prikaz: ```-ld```) - ukazuje, od kterych uzlu dany uzel zavisi.
- ```get_type``` - vraci typ uzlu (ACTIVE nebo PASSIVE).
- ```get_state``` - vraci stav uzlu, pouziva se pro interni ucely behu aplikace, napriklad pro typ pozadavku v kriticke sekce (PROVOKING, CREATE_SINGLE_DEPEND, ANSWER_SINGLE, DETECTING, IDLE).
- ```get_count``` - vraci pocet aktivnich uzlu v systemu.
- ```provoke_full_deadlock``` (prislusny konzolovy prikaz: ```-pdf```) - provadi process zablokovani uzlu, kazdy uzel nahodne vybira z mnoziny znamych uzlu a posila jim pozadavky, tim samym stava na nej zavisly a formuje svoji mnozinu zavislosti. Kazdy uzel muze byt zavisly na 0-2 jinych uzlech.
- ```provoke_single/{receiverId}``` (prislusny konzolovy prikaz: ```-pds {receiverId}```) - provadi jeden pozadavek zpusobujici zavislost. takovym zpusobem se da nastavit vlastni zablokovani.
- ```answer_single/{receiverId}``` (prislusny konzolovy prikaz: ```-as {receiverId}```) - posila "odpoved" na pozadavek zpusobujici zavislost, takova zprava znici zavislost uzlu, kteremu zprava byla odeslana.
- ```detect_deadlock``` (prislusny konzolovy prikaz: ```-dd```) - spusti proces detekovani zablokovani. Muze byt spusten z libovolneho uzlu. Pokud zablokovani existuje, initiator az se o tom dozvedi, informuje vsichni uzly.
- ```clear``` (prislusny konzolovy prikaz: ```-cd```) - smaze vsechny zavislosti a vrati uzly do pocatecniho stavu.
- ```set_active``` - nastavi typ uzlu na ACTIVE, pokud je to mozne.
- ```set_passive``` - nastavi typ uzlu na PASSIVE.
- ```exit``` (prislusny konzolovy prikaz: ```-out```) - zabije uzel. Pred vypnutim uzel odesle vsem oznameni ze se odpoji.

## Procesy aplikace
### Detekce zablokovani
Pro detekci byl vyuzit Chandy-Misra-Hass algoritmus (a pseudokod) z prednasek. Pro ucely teto aplikace byl vybran typ zablokovani na komunikace.
### Kriticka sekce
Pro spusteni samotneho procesu detekce zablokovani, aplikace potrebuje vytvorit nejakou "simulaci" zablokovani (hlavne randomizovany provoke_full_deadlock). Pro tento proces aplikace vyuziva vysilani pres kritickou sekci. Proces vytvoreni zablokovani neni moc slozity, nicmene, aby zablokovani ne skoncilo na dvou uzlech spojenych mezi sebou kvuli soubeznym zadostem, pro vysilani pozadavku na zavislost uzel ma vstoupit do kriticke sekce a v tuto chvili muze pozadat jen uzly ktere nejsou na nej uz zavisle.
Samotna detekce nevyuziva kritickou sekci, protoze tento proces nezpusobi zmeny ve stavech uzlu a neovlivnuje kriticka data.
### JMS
Aplikace pouziva 4 topiky pro komunikaci, dve verejne (broadcast a heartbeat) a dve pouzivajici selektory pro "osobni" komunikaci (direct a detectDirect). Selektorami slouzi id uzlu. Pro kazdy topik existuje vlastni Listener, ktery prijima zpravy a zpracovava je.
### Heartbeat
Heartbeat neni soucasti zakladniho algoritmu detekce zablokovani, ale v ramci dane aplikaci slouzi pro dve veci:
- detekce spadnuti uzlu. Pokud behem pevneho casu uzel nedostava heartbeat zpravy, rozhodne ze uzel neni aktivni (spadnul), a odstrani ho ze svych dat (upravi pocet uzlu, mozne zavislosti, pripadne odstrani z aktivnich zavislosti).
- detekce nepritomnosti zablokovani. Pokud uzel neni v deadlocku, ale dotazuje se na detekce, na zaklade nepritomnosti odpovedi jinych uzlu ale pritomnosti od nej heartbeatu, jednotlivy uzel je schopen rozhodnout ze nedoslo k zablokovani. 
### Pamet uzlu
Uzly nemaji nejak definovane "sousedy" nebo nejakou konkretni topologii. Komunikuji pres brokera, maji jednoznacne identifikatory. Nicmene, kazdy uzel vysila Join zpravu jakmize se probudi, protoze pro spravne fungovani systemu uzel potrebuje vedet pocet aktivnich uzlu a jejich id. Seznam id je nutny k generovani samotneho zablokovani, aby uzly mohli "vybirat" komu posilat zpravy a nasledne se "blokovat", a taky pro souhlas na vstup do kriticke sekce . Pro proces detekce tyto data se taky pouziva - pro generovani Last, Wait, Parent a Number seznamu.

## Logy
Uzly generuji logy v prubehu sve prace, ktere se vypisuji do konzoli a taky do souboru ```app.log```.