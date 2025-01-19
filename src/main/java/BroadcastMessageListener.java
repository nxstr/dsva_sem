import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.Objects;

public class BroadcastMessageListener implements MessageListener {

    private static final Logger logger = LogManager.getLogger(BroadcastMessageListener.class);

    private final Node node;
    @Getter
    private volatile boolean messageReceived = false;
    private Integer senderId;
    private String messageText;
    private CSRequest request;

    public BroadcastMessageListener(Node node) {
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
        if(messageText.startsWith("JOIN") && !Objects.equals(senderId, node.getNodeId())){
            if(!node.getNodeIds().contains(senderId)) {
                node.getPossibleIds().add(senderId);
                node.getNodeIds().add(senderId);
                node.sendDirectAck(senderId, "JACK");
                node.addNewNodeToLists(senderId);
            }
            logger.info(node.getNodeLogName()+ "Got join in node " + node.getNodeId() + ", current node count: " + node.getNodeIds().size());
        }else if(messageText.startsWith("REQUEST") && !Objects.equals(senderId, node.getNodeId())){
            if(node.checkRequestRepeat(request)){
                boolean isNewWinner = node.getRequestQueue().get(0).getNodeId() == request.getNodeId();
                node.sendCSResponse(isNewWinner, request.getNodeId());
                return;
            }
            node.setLogicalClock(Math.max(request.getTimestamp(), node.getLogicalClock())+1);
            boolean isNewWinner = node.insertRequest(request);
            node.sendCSResponse(isNewWinner, request.getNodeId());
        }else if(messageText.startsWith("RELEASE") && !Objects.equals(senderId, node.getNodeId())){
            node.setLogicalClock(Math.max(request.getTimestamp(), node.getLogicalClock())+1);
            node.getRequestQueue().remove(0);
            for(CSRequest r: node.getRequestQueue()){
                logger.info(node.getNodeLogName()+ "Release message from: " + r.getNodeId() + " timestamp: " + r.getTimestamp());
            }
            if(!node.getRequestQueue().isEmpty() && node.getRequestQueue().get(0).getNodeId()== node.getNodeId()){
                node.repeatRequestToCriticalSection();
            }
        }else if(messageText.startsWith("DEADLOCK") && !Objects.equals(senderId, node.getNodeId()) && node.getType()==NodeType.PASSIVE){
            logger.info(node.getNodeLogName()+ "DEADLOCK DETECTED on node " + senderId);
            node.setState(NodeState.IDLE);
        }else if(messageText.startsWith("CLEAR") && !Objects.equals(senderId, node.getNodeId())){
            logger.info(node.getNodeLogName()+ "CLEAR DETECTED on node " + senderId);
            node.setState(NodeState.IDLE);
            node.clearDeadlock();
        }else if(messageText.startsWith("EXIT") && !Objects.equals(senderId, node.getNodeId())){
            logger.warn(node.getNodeLogName()+ "Node " + senderId + " is considered inactive.");
            node.getActiveNodes().entrySet().removeIf(entry -> Objects.equals(entry.getKey(), senderId));
            node.removeInactiveNode(senderId);
        }
    }

    private void convertMessage(TextMessage message) throws JMSException {
        senderId = Integer.parseInt(message.getText().split("\\|")[0]);
        if(message.getText().split("\\|").length==3){
            request = new CSRequest(Integer.parseInt(message.getText().split("\\|")[1]), senderId);
            messageText = message.getText().split("\\|")[2];
        }else if(message.getText().split("\\|").length==4){
            request = new CSRequest(Integer.parseInt(message.getText().split("\\|")[1]), senderId);
            messageText = message.getText().split("\\|")[2] + "|"+ message.getText().split("\\|")[3];
        }else {
            messageText = message.getText().split("\\|")[1];
        }
    }



}