import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class HeartbeatListener implements MessageListener {

    private static final Logger logger = LogManager.getLogger(HeartbeatListener.class);

    private final Node node;
    @Getter
    private volatile boolean messageReceived = false;
    private Integer senderId;

    public HeartbeatListener(Node node) {
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
    private void processMessage()throws JMSException {
        if(!Objects.equals(senderId, node.getNodeId())) {
            if(node.getActiveNodes()==null){
                node.setActiveNodes(new ConcurrentHashMap<>());
            }

            if(node.getActiveNodes().containsKey(senderId)){
                node.getActiveNodes().replace(senderId, System.currentTimeMillis());
            }else{
                node.getActiveNodes().putIfAbsent(senderId, System.currentTimeMillis());
            }
//            System.out.println("Received heartbeat from node: " + senderId + " at time " + System.currentTimeMillis());
        }
    }

    private void convertMessage(TextMessage message) throws JMSException {
        senderId = Integer.parseInt(message.getText());
    }
}
