import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.Objects;

public class DirectMessageListener implements MessageListener {
    private static final Logger logger = LogManager.getLogger(BroadcastMessageListener.class);

    private final Node node;
    @Getter
    private volatile boolean messageReceived = false;
    @Getter
    private volatile String receivedMessageType = "";
    private Integer senderId;
    private String messageText;
    private CSRequest request;
    private int agreementCount;
    private int responseCount;
    public DirectMessageListener(Node node) {
        this.node = node;
    }

    @Override
    public void onMessage(Message message) {
        if (message instanceof TextMessage) {
            try {
                convertMessage((TextMessage) message);
                processMessage();
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }

    public void processMessage() throws JMSException {
//        if(node.getNodeId()==2 || node.getNodeId()==4) {
//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        messageReceived = false;
        receivedMessageType = "";
        if(messageText.startsWith("JACK")){
            messageReceived = true;
            receivedMessageType = "JACK";
//            node.setCurrentNodeCount(node.getCurrentNodeCount()+1);
            if(!node.getNodeIds().contains(senderId)) {
                node.getPossibleIds().add(senderId);
                node.getNodeIds().add(senderId);
                node.addNewNodeToLists(senderId);
            }
            logger.info(node.getNodeLogName()+ "Got join ack from " + senderId + " in node " + node.getNodeId() + ", current node count: " + node.getNodeIds().size());
        }else if(messageText.startsWith("PACK")){
            messageReceived = true;
            receivedMessageType = "PACK";
            node.setReceiverId(0);
            if(node.getState()!=NodeState.ANSWER_SINGLE) {
                node.getListOfDependencies().add(senderId);
            }
            if(node.getListOfDependencies().size()==node.getDependencyCount()){
                node.setState(NodeState.IDLE);
            }
            logger.info(node.getNodeLogName()+ "Got Provoke ack from " + senderId + ", in node " + node.getNodeId());
        }
        else if(messageText.startsWith("PROVOKE")){
            messageReceived = true;
            receivedMessageType = "PROVOKE";
            logger.info(node.getNodeLogName()+ "Got provoke on node " + node.getNodeId() + " from node " + senderId);
            node.getPossibleIds().remove(senderId);
            node.sendDirectAck(senderId, "PACK");
            if(node.getType()==NodeType.ACTIVE){
                node.setState(NodeState.PROVOKING);
                node.repeatRequestToCriticalSection();
            }
        }else if(messageText.startsWith("SINGLE")){
            logger.info(node.getNodeLogName()+ "Got single provoke on node " + node.getNodeId() + " from node " + senderId);
            node.getPossibleIds().remove(senderId);
            node.sendDirectAck(senderId, "PACK");
        }else if(messageText.startsWith("S_ANSWER")){
            logger.info(node.getNodeLogName()+ "Got single answer on node " + node.getNodeId() + " from node " + senderId);
            if(node.getListOfDependencies().contains(senderId)) {
                node.getPossibleIds().add(senderId);
                node.setDependencyCount(node.getDependencyCount() - 1);
                node.getListOfDependencies().remove(senderId);
                node.setState(NodeState.IDLE);
                if (node.getListOfDependencies().isEmpty()) {
                    node.setType(NodeType.ACTIVE);
                }
            }else{
                logger.info(node.getNodeLogName()+ "Node does not have dependency upon node " + senderId);
            }
            node.sendDirectAck(senderId, "PACK");
        }
        else if(messageText.startsWith("RESPONSE") && node.isCriticalSectionRequested()){
            if(messageText.split("\\|")[1].equals("YES")){
                agreementCount++;
                responseCount++;
                if(responseCount==node.getNodeIds().size()-1){
                    node.setLogicalClock(Math.max(request.getTimestamp(), node.getLogicalClock())+1);
                    if(agreementCount==node.getNodeIds().size()-1){
                        node.enterCriticalSection();
                    }
                    agreementCount = 0;
                    responseCount = 0;
                }
            }
        }
    }

    private void convertMessage(TextMessage message) throws JMSException {
        senderId = Integer.parseInt(message.getText().split("\\|")[0]);
        if(message.getText().split("\\|").length==4){
            request = new CSRequest(Integer.parseInt(message.getText().split("\\|")[1]), senderId);
            messageText = message.getText().split("\\|")[2] + "|"+ message.getText().split("\\|")[3];
        }else if(message.getText().split("\\|").length==3){
            messageText = message.getText().split("\\|")[1] + "|"+ message.getText().split("\\|")[2];
        }else {
            messageText = message.getText().split("\\|")[1];
        }
    }
}
