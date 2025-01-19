import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

public class DetectDirectListener implements MessageListener {
    private static final Logger logger = LogManager.getLogger(BroadcastMessageListener.class);

    private final Node node;
    private Integer originatorId;
    private Integer senderId;
    private Integer receiverId;
    private Integer testNum;
    private String messageText;
    public DetectDirectListener(Node node) {
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
        if(messageText.startsWith("QUESTION")){

            if(node.getType() == NodeType.PASSIVE){
                node.setState(NodeState.DETECTING);
                node.processQuestion(originatorId, testNum, senderId);
            }
        }else if(messageText.startsWith("ANSWER")){
            if(node.getType() == NodeType.PASSIVE){
                node.processAnswer(originatorId,testNum, receiverId);
            }
        }
    }

    private void convertMessage(TextMessage message) throws JMSException {
        messageText = message.getText().split("\\|")[0];
        originatorId = Integer.parseInt(message.getText().split("\\|")[1]);
        testNum = Integer.parseInt(message.getText().split("\\|")[2]);
        senderId = Integer.parseInt(message.getText().split("\\|")[3]);
        receiverId = Integer.parseInt(message.getText().split("\\|")[4]);
    }
}
