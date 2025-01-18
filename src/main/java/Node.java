import lombok.Getter;
import lombok.Setter;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import javax.jms.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Node {
    private static final Logger logger = LogManager.getLogger(Node.class);
    public static Node thisNode = null;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;
    private Topic broadcastTopic;
    private Topic directTopic;
    private Topic detectDirectTopic;
    private Topic heartbeatTopic;
    private MessageProducer broadcastProducer;
    private MessageProducer directProducer;
    private MessageProducer detectDirectProducer;
    private MessageProducer heartbeatProducer;
    private MessageConsumer broadcastConsumer;
    private MessageConsumer directConsumer;
    private MessageConsumer detectDirectConsumer;
    private MessageConsumer heartbeatConsumer;
    private final String firstBrokerAddr;
    @Getter
    @Setter
    private List<Integer> listOfDependencies = new ArrayList<>();
    @Getter
    @Setter
    private List<Integer> possibleIds = new ArrayList<>();
    @Getter
    @Setter
    private List<Integer> nodeIds = new ArrayList<>();

    private final String secondBrokerAddr;
    @Getter
    @Setter
    private Integer nodeId;
    @Getter
    @Setter
    private NodeType type;
    @Getter
    @Setter
    private NodeState state = NodeState.IDLE;
    @Getter
    @Setter
    private boolean isCriticalSectionRequested = false;

    @Getter
    @Setter
    private int logicalClock = 0;
    @Getter
    private final List<CSRequest> requestQueue = new ArrayList<>();

    //chm
    @Getter
    @Setter
    private List<Pair> last = new ArrayList<>();
    @Getter
    @Setter
    private List<Pair> wait = new ArrayList<>();
    @Getter
    @Setter
    private List<Pair> parent = new ArrayList<>();
    @Getter
    @Setter
    private List<Pair> number = new ArrayList<>();
    @Getter
    @Setter
    private boolean isInitiator = false;
    @Getter
    @Setter
    private int dependencyCount = 0;
    @Getter
    @Setter
    private int receiverId = 0;
    @Getter
    private APIHandler myAPIHandler;
    @Getter
    private Timer heartbeatTimer;
    @Getter
    private long heartbeatInterval = 1000; // 5 seconds
    @Getter
    @Setter
    private Map<Integer, Long> activeNodes;
    @Getter
    @Setter
    private Map<Integer, Long> detectionTimes;
    @Getter
    @Setter
    private int delayMillis = 0;
    @Getter
    private String nodeLogName = "[Node ";


    public Node(String[] args) {
        if(args.length<3){
            System.exit(1);
        }
        if(args.length==4){
            delayMillis = Integer.parseInt(args[3]);
        }
        firstBrokerAddr = args[1];
        secondBrokerAddr = args[2];
        nodeId = Integer.parseInt(args[0]);
        nodeLogName += nodeId + "] ";
        myAPIHandler = new APIHandler(this, 5000 + nodeId);
        type = NodeType.ACTIVE;
        addNewNodeToLists(nodeId);
        configureJMS();
        myAPIHandler.start();
        this.activeNodes = new HashMap<>();
        detectionTimes = new HashMap<>();
    }
    private void configureJMS(){
        try{
            connectionFactory = new ActiveMQConnectionFactory("failover:(tcp://"+firstBrokerAddr+":61616)");
//            connectionFactory = new ActiveMQConnectionFactory("failover:(tcp://"+firstBrokerAddr+":61616,tcp://"+secondBrokerAddr+":61616)");
            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            broadcastTopic = session.createTopic("VirtualTopic.BroadcastTopic");
            directTopic = session.createTopic("VirtualTopic.DirectTopic");
            detectDirectTopic = session.createTopic("VirtualTopic.DetectDirectTopic");
            heartbeatTopic = session.createTopic("VirtualTopic.HeartbeatTopic");
            String selector = "nodeId = " + nodeId;

            broadcastProducer = session.createProducer(broadcastTopic);
            directProducer = session.createProducer(directTopic);
            detectDirectProducer = session.createProducer(detectDirectTopic);
            heartbeatProducer = session.createProducer(heartbeatTopic);
            broadcastConsumer = session.createConsumer(broadcastTopic);
            directConsumer = session.createConsumer(directTopic, selector);
            detectDirectConsumer = session.createConsumer(detectDirectTopic, selector);
            heartbeatConsumer = session.createConsumer(heartbeatTopic);

            BroadcastMessageListener joinListener = new BroadcastMessageListener(this);
            broadcastConsumer.setMessageListener(joinListener);


            DirectMessageListener directListener = new DirectMessageListener(this);
            directConsumer.setMessageListener(directListener);

            DetectDirectListener detectDirectListener = new DetectDirectListener(this);
            detectDirectConsumer.setMessageListener(detectDirectListener);

            HeartbeatListener heartbeatListener = new HeartbeatListener(this);
            heartbeatConsumer.setMessageListener(heartbeatListener);
//            MessageConsumer critRequestConsumer = session.createConsumer(critRequestTopic);
//            topicConsumerMap.put(TopicName.CRIT_TOPIC, critRequestConsumer);
//            CritMessageListener critListener = new CritMessageListener(this);
//            critRequestConsumer.setMessageListener(critListener);


            sendJoinMessage();
            Thread.sleep(1000);
            nodeIds.add(nodeId);
            if (!directListener.isMessageReceived()) {
//                logger.info("Node " + nodeName + ": " +"No messages received. Setting nodeId to 1. I am first.");
//                nodeId = 1;
//                actualCount = 1;
                logger.info(nodeLogName+ "No one responded. I am single node");
            }
            logger.info(nodeLogName+ "Current node count: " + nodeIds.size());
            startHeartbeat();
            startNodeMonitoring();
        } catch (JMSException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static void handleInputCommand() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                if (!scanner.hasNextLine()) {
                    continue;
                }
                String input = scanner.nextLine();
                if(thisNode.getNodeId()!=-1) {
                    if ("-pdf".equals(input)) {
                        thisNode.executeProvokeDeadlock();
                    }else if(input.startsWith("-pds")){
                        thisNode.executeProvokeSingleDependency(Integer.parseInt(input.split(" ")[1]));
                    }else if(input.startsWith("-as")){
                        thisNode.executeAnswerSingle(Integer.parseInt(input.split(" ")[1]));
                    }else if("-ld".equals(input)){
                        thisNode.getDependencies();
                    }else if("-dd".equals(input)){
                        thisNode.executeDetectDeadlock();
                    }else if("-cd".equals(input)){
                        thisNode.executeClearDeadlock();
                    }else if("-sp".equals(input)){
                        thisNode.setPassive();
                    }else if("-sa".equals(input)){
                        thisNode.setActive();
                    }else if("-out".equals(input)){
                        thisNode.shutdown();
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            thisNode = new Node(args);
            Thread consoleListenerThread = new Thread(Node::handleInputCommand);
            consoleListenerThread.start();
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    private void startNodeMonitoring() {
        Timer monitoringTimer = new Timer(true);
        monitoringTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkForInactiveNodes(15000+(delayMillis* 2L)); // 10-second timeout
            }
        }, 0, 5000+delayMillis); // Check every 5 seconds
    }
    public void checkForInactiveNodes(long timeout) {
        long currentTime = System.currentTimeMillis();
//        activeNodes.forEach((key, value) -> {
//                    System.out.println("Node " + key + " last heartbeat at " + value + ", current time: " + currentTime);
//                });
        if(detectionTimes==null){
            detectionTimes = new HashMap<>();
        }
        activeNodes.entrySet().removeIf(entry -> {
            boolean inactive = (currentTime - entry.getValue()) > timeout;
            if (inactive) {
                logger.warn(nodeLogName+ "Node " + entry.getKey() + " is considered inactive.");
                if(state==NodeState.DETECTING && detectionTimes.containsKey(entry.getKey())){
                    logger.warn(nodeLogName+ "Please retry deadlock detection process");
                }
                removeInactiveNode(entry.getKey());
            }else if(!inactive && state==NodeState.DETECTING && detectionTimes.containsKey(entry.getKey()) && (currentTime - detectionTimes.get(entry.getKey()))>timeout){
                logger.info(nodeLogName+ "Node " + nodeId + " is not deadlocked");
                state = NodeState.IDLE;
            }
            return inactive;
        });
    }

    public void startHeartbeat() {
        if(activeNodes==null){
            activeNodes = new ConcurrentHashMap<>();
        }
        heartbeatTimer = new Timer(true);
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    TextMessage heartbeat = session.createTextMessage(String.valueOf(nodeId));
                    heartbeatProducer.send(heartbeat);
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        }, 0, heartbeatInterval);
    }

    public void shutdown(){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId+"|EXIT");
            logger.info(nodeLogName+ "Node " + nodeId + " is sending exit message");
            broadcastProducer.send(message);
            System.exit(0);
        }catch (JMSException e){
            e.printStackTrace();
        }
    }
    public void executeDetectDeadlock(){
        setState(NodeState.DETECTING);
        setInitiator(true);
        startDetection();
    }
    public void executeClearDeadlock(){
        setState(NodeState.IDLE);
        repeatRequestToCriticalSection();
    }

    public void executeAnswerSingle(int receiverId){
        thisNode.setState(NodeState.ANSWER_SINGLE);
        thisNode.setReceiverId(receiverId);
        thisNode.repeatRequestToCriticalSection();
    }
    public void executeProvokeSingleDependency(int receiverId){
        if(!thisNode.getListOfDependencies().contains(receiverId)) {
            thisNode.setState(NodeState.CREATE_SINGLE_DEPEND);
            thisNode.setReceiverId(receiverId);
            thisNode.repeatRequestToCriticalSection();
        }else{
            logger.info(nodeLogName+ "Node already has dependency on node: " + receiverId);
        }
    }

    public void executeProvokeDeadlock(){
        if(type==NodeType.ACTIVE) {
            setState(NodeState.PROVOKING);
            repeatRequestToCriticalSection();
        }else{
            logger.info(nodeLogName+ "Node " + nodeId + ": "+"Node is in PASSIVE state");
        }
    }

    public void setPassive(){
        type = NodeType.PASSIVE;
        state = NodeState.IDLE;
    }

    public void setActive(){
        if(listOfDependencies.isEmpty()){
            type = NodeType.ACTIVE;
            state = NodeState.IDLE;
        }
    }

    public List<Integer> getDependencies(){
        for(Integer i: listOfDependencies){
            logger.info(nodeLogName+ "Dependent upon node: " + i);
        }
        return listOfDependencies;
    }
    public void sendJoinMessage(){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId+"|JOIN");
            logger.info(nodeLogName+ "Node " + nodeId + " is sending join message");
            broadcastProducer.send(message);
//            startPinging();
        }catch (JMSException e){
            e.printStackTrace();
        }
    }

    public void sendDirectAck(int receiverId, String type){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId+"|"+ type);
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending ack message to " + receiverId);
            directProducer.send(message);
//            startPinging();
        }catch (JMSException e){
            e.printStackTrace();
        }
    }

    public void requestToCriticalSection(){
        logicalClock++;
        isCriticalSectionRequested = true;
        CSRequest request = new CSRequest(logicalClock, nodeId);
        requestQueue.add(request);
        sendRequestToCS(request);
    }

    public void repeatRequestToCriticalSection(){
        for(CSRequest r: requestQueue){
            if(r.getNodeId()==nodeId){
                sendRequestToCS(r);
                return;
            }
        }
        requestToCriticalSection();
    }

    public boolean checkRequestRepeat(CSRequest request){
        for(CSRequest r: requestQueue){
            if(r.getNodeId()==request.getNodeId()){
                return true;
            }
        }
        return false;
    }

    public void sendRequestToCS(CSRequest request){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(request.getNodeId()+ "|"+ request.getTimestamp() +"|REQUEST");
            logger.info(nodeLogName+ "Node " + nodeId + " is sending request to cs");
            broadcastProducer.send(message);
        }catch (JMSException e){
            e.printStackTrace();
        }
    }

    public boolean insertRequest(CSRequest newRequest) {
        boolean added = false;
        boolean isNewWinner = false;
        for (int i = 0; i < requestQueue.size(); i++) {
            CSRequest existingRequest = requestQueue.get(i);
            if(i==0){
                int winnerId = existingRequest.getWinnerNodeId(newRequest);
                if(winnerId== newRequest.getNodeId()){
                    isNewWinner = true;
                }
            }

            // If the new request has a smaller timestamp (higher priority), insert it
            if (newRequest.getTimestamp() < existingRequest.getTimestamp()) {
                requestQueue.add(i, new CSRequest(newRequest.getTimestamp(), newRequest.getNodeId()));
                added = true;
                break;
            }
            // If timestamps are equal, compare nodeId
            if (newRequest.getTimestamp() == existingRequest.getTimestamp() &&
                    newRequest.getNodeId() < existingRequest.getNodeId()) {
                requestQueue.add(i, new CSRequest(newRequest.getTimestamp(), newRequest.getNodeId()));
                added = true;
                break;
            }
        }
        if(requestQueue.isEmpty()){
            isNewWinner = true;
        }
        // If the request wasn't inserted during the loop, it should go to the end of the list
        if (!added) {
            requestQueue.add(new CSRequest(newRequest.getTimestamp(), newRequest.getNodeId()));
        }
        return isNewWinner;
    }
    public void sendCSResponse(boolean isNewWinner, int receiverId){
        try {
            String answer = "YES";
            if(!isNewWinner){
                answer = "NO";
            }
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId+ "|"+ logicalClock +"|RESPONSE|" + answer);
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending response " + answer + " of cs to " + receiverId);
            directProducer.send(message);
        }catch (JMSException e){
            e.printStackTrace();
        }
    }

    public void enterCriticalSection(){
//        sendHeartBeats();
        logger.info(nodeLogName+ "Node " + nodeId + " is entering critical section");
        boolean isClearAnnounced = false;
        if(state==NodeState.PROVOKING) {
            provokeDeadlock();
        }else if(state==NodeState.CREATE_SINGLE_DEPEND){
            waitForMessage(receiverId);
        }else if(state==NodeState.ANSWER_SINGLE){
            sendMessage(receiverId);
        }
        else if(state == NodeState.DETECTING && isInitiator){
            announceDeadlock();
            state = NodeState.IDLE;
        }else if(state==NodeState.IDLE) {
            announceClearDeadlock();
            isClearAnnounced = true;
        }
        leaveCriticalSection();
        if(isClearAnnounced){
            clearDeadlock();
        }
    }

    public void announceDeadlock(){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId+"|DEADLOCK");
            logger.info(nodeLogName+ "Node " + nodeId + " is sending deadlock announce from cs");
            broadcastProducer.send(message);
        }catch (JMSException e){
            e.printStackTrace();
        }
    }

    public void announceClearDeadlock(){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId+"|CLEAR");
            logger.info(nodeLogName+ "Node " + nodeId + " is sending clear message from cs");
            broadcastProducer.send(message);
        }catch (JMSException e){
            e.printStackTrace();
        }
    }
    public void clearDeadlock(){
        type = NodeType.ACTIVE;
        listOfDependencies.clear();
        possibleIds.clear();
        for(int id: nodeIds){
            if(id!=nodeId) {
                possibleIds.add(id);
            }
        }
//        requestQueue.clear();
        dependencyCount = 0;
        isCriticalSectionRequested = false;
        isInitiator = false;
    }

    public void removeInactiveNode(int inactiveId){
//        currentNodeCount--;
        if(listOfDependencies.contains(inactiveId)){
            listOfDependencies.remove(listOfDependencies.indexOf(inactiveId));
            if(listOfDependencies.isEmpty()){
                type = NodeType.ACTIVE;
                dependencyCount = 0;
            }
            dependencyCount--;
        }
        if(possibleIds.contains(inactiveId)) {
            possibleIds.remove(possibleIds.indexOf(inactiveId));
        }
        if(nodeIds.contains(inactiveId)) {
            nodeIds.remove(nodeIds.indexOf(inactiveId));
        }
        removeNodeToList(inactiveId);
        requestQueue.removeIf(request -> request.getNodeId()==inactiveId);
        if(!requestQueue.isEmpty() && requestQueue.get(0).getNodeId()== nodeId){
            repeatRequestToCriticalSection();
        }
    }


    public void sendReleaseFromCS(CSRequest request){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(request.getNodeId()+ "|"+ request.getTimestamp() +"|RELEASE");
            logger.info(nodeLogName+ "Node " + nodeId + " is sending release from cs");
            broadcastProducer.send(message);
        }catch (JMSException e){
            e.printStackTrace();
        }
    }
    public void leaveCriticalSection(){
        logger.info(nodeLogName+ "Node " + nodeId + " is leaving cs");
        CSRequest request = new CSRequest(logicalClock, nodeId);
        isCriticalSectionRequested = false;
        requestQueue.remove(0);
        sendReleaseFromCS(request);
    }


    public void provokeDeadlock(){
        Random random = new Random();
        int dependencySize = 0;
        dependencySize = possibleIds.isEmpty() ?0:random.nextInt(possibleIds.size());
        dependencySize++;
        if(dependencySize>2){
            dependencySize = 2;
        }
        for(int i=0; i<dependencySize; i++){
            if(possibleIds.isEmpty()){
                break;
            }
            int idx = random.nextInt(possibleIds.size());
            int nodeDependId = possibleIds.get(idx);
            possibleIds.remove(idx);
//            if(!listOfDependencies.contains(nodeDependId)){
            sendProvokeDirectMessage(nodeDependId);
            dependencyCount++;
//            }
        }
    }

    public void waitForMessage(int receiverId){
//        setState(NodeState.MANUAL_DEPEND);
        if(possibleIds.contains(receiverId)) {
            String type = "SINGLE";
            dependencyCount++;
            sendSingleProvokeMessage(receiverId, type);
            possibleIds.remove(possibleIds.indexOf(receiverId));
        }else{
            logger.info(nodeLogName+ "Sending provoke to node " + receiverId + " is not possible");
        }
    }

    public void sendMessage(int receiverId){
        possibleIds.add(receiverId);
        String type = "S_ANSWER";
        sendSingleAnswerMessage(receiverId, type);
        this.type = NodeType.ACTIVE;
    }

    public void sendSingleAnswerMessage(int receiverId, String msg){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId + "|" + msg);
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending single answer message to " + receiverId);
            directProducer.send(message);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void sendSingleProvokeMessage(int receiverId, String msg){
        try {
            if(type==NodeType.ACTIVE){
                type = NodeType.PASSIVE;
            }
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage(nodeId + "|" + msg);
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending single provoke message to " + receiverId);
            directProducer.send(message);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }


    public void sendProvokeDirectMessage(int receiverId) {
        try {
            if(type==NodeType.ACTIVE){
                type = NodeType.PASSIVE;
            }
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            String type = "FULL";
            TextMessage message = session.createTextMessage(nodeId + "|PROVOKE");
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending provoke message to " + receiverId);
            directProducer.send(message);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void addNewNodeToLists(int newNodeId){
        if(last.stream().noneMatch(d->d.getNodeId()==newNodeId)){
            last.add(new Pair(newNodeId, 0));
        }
        if(wait.stream().noneMatch(d->d.getNodeId()==newNodeId)){
            wait.add(new Pair(newNodeId, 0));
        }
        if(parent.stream().noneMatch(d->d.getNodeId()==newNodeId)){
            parent.add(new Pair(newNodeId, 0));
        }
        if(number.stream().noneMatch(d->d.getNodeId()==newNodeId)){
            number.add(new Pair(newNodeId, 0));
        }
    }
    public void removeNodeToList(int inactiveId){
        last.removeIf(pair -> pair.getNodeId() == inactiveId);
        wait.removeIf(pair -> pair.getNodeId() == inactiveId);
        parent.removeIf(pair -> pair.getNodeId() == inactiveId);
        number.removeIf(pair -> pair.getNodeId() == inactiveId);
    }


    public void startDetection(){
        if(type==NodeType.PASSIVE){
            if(listOfDependencies.isEmpty()){
                logger.info(nodeLogName+ "Node " + nodeId + " is not deadlocked");
                return;
            }
            int testNum = 0;
            for(Pair p: last){
                if(p.getNodeId()==nodeId){
                    p.setValue(p.getValue()+1);
                    testNum = p.getValue();
                    break;
                }
            }
            for(Pair p: wait){
                if(p.getNodeId()==nodeId){
                    p.setValue(1);
                    break;
                }
            }
            for(Integer id: listOfDependencies){
                sendQuestion(nodeId, testNum, nodeId, id); //originatorId, testNum from last, sender, receiverId,
            }
            for(Pair p: number){
                if(p.getNodeId()==nodeId){
                    p.setValue(listOfDependencies.size());
                    break;
                }
            }
        }else if(type==NodeType.ACTIVE){
            logger.info(nodeLogName+ "Node " + nodeId + " is not deadlocked");
        }
    }

    public void sendQuestion(int originatorId, int testNum, int senderId, int receiverId){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage("QUESTION|" + originatorId + "|" + testNum + "|" + senderId + "|" + receiverId);
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending question message to " + receiverId);

            if(detectionTimes==null){
                detectionTimes = new HashMap<>();
            }
            if(detectionTimes.containsKey(receiverId)){
                detectionTimes.replace(receiverId, System.currentTimeMillis());
            }else{
                detectionTimes.putIfAbsent(receiverId, System.currentTimeMillis());
            }
            detectDirectProducer.send(message);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void sendAnswer(int originatorId, int testNum, int senderId, int receiverId){
        try {
            if(delayMillis!=0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            TextMessage message = session.createTextMessage("ANSWER|" + originatorId + "|" + testNum + "|" + senderId + "|" + receiverId);
            message.setIntProperty("nodeId", receiverId);
            logger.info(nodeLogName+ "Node " + nodeId + " is sending answer message to " + receiverId);
            detectDirectProducer.send(message);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public void processQuestion(int originatorId, int testNum, int senderId){
        for(Pair p: last){
            if(p.getNodeId()==originatorId){
                if(testNum> p.getValue()){
                    p.setValue(testNum);
                    for(Pair p1: wait){
                        if(p1.getNodeId()==originatorId){
                            p1.setValue(1);
                            break;
                        }
                    }
                    for(Pair p1: parent){
                        if(p1.getNodeId()==originatorId){
                            p1.setValue(senderId);
                            break;
                        }
                    }
                    for(Integer id: listOfDependencies){
                        sendQuestion(originatorId, testNum, nodeId, id); //originatorId, testNum from last, sender, receiverId,
                    }
                    for(Pair p1: number){
                        if(p1.getNodeId()==originatorId){
                            p1.setValue(listOfDependencies.size());
                            break;
                        }
                    }
                }else{
                    int waitV = wait.stream().filter(p1->p1.getNodeId()==originatorId).findAny().orElse(null).getValue();
                    if(waitV==1 && p.getValue()==testNum){
                        sendAnswer(originatorId, testNum, nodeId, senderId); //originatorId, testNum from last, sender, receiverId,
                    }
                }
                break;
            }
        }
    }

    public void processAnswer(int originatorId, int testNum, int receiverId){
        int waitV = wait.stream().filter(p1->p1.getNodeId()==originatorId).findAny().orElse(null).getValue();
        int lastV = last.stream().filter(p1->p1.getNodeId()==originatorId).findAny().orElse(null).getValue();
        if(waitV==1 && lastV==testNum){
            for(Pair p1: number){
                if(p1.getNodeId()==originatorId){
                    p1.setValue(p1.getValue()-1);
                    if(p1.getValue()==0){
                        if(originatorId==receiverId && receiverId==nodeId){
                            logger.info(nodeLogName+ "DEADLOCK DETECTED on node " + nodeId);
                            requestToCriticalSection();
                        }else{
                            Pair parent1 = parent.stream().filter(p->p.getNodeId()==originatorId).findAny().orElse(null);
                            if(parent1!=null) {
                                sendAnswer(originatorId, testNum, receiverId, parent1.getValue()); //originatorId, testNum from last, sender, receiverId,
                                for(Pair p: wait){
                                    if(p.getNodeId()==originatorId){
                                        p.setValue(0);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }
    }
//    public void sendDeadlockFreeMsg(int originatorId, int testNum, int receiverId){
//        try {
//            TextMessage message = session.createTextMessage("DFM|" + originatorId + "|" + testNum + "|" + nodeId + "|" + receiverId);
//            message.setIntProperty("nodeId", receiverId);
//            System.out.println("Node " + nodeId + " is sending dfm message to " + receiverId);
//            detectDirectProducer.send(message);
//        } catch (JMSException e) {
//            e.printStackTrace();
//        }
//    }

}
