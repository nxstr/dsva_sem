import lombok.Getter;
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
    private int dfmCount;
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
//        if(node.getNodeId()==2 || node.getNodeId()==4) {
//            try {
//                Thread.sleep(2000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
        if(messageText.startsWith("QUESTION")){

            if(node.getType() == NodeType.PASSIVE){
                System.out.println("Question from " + senderId);
//                if(node.getLast().stream().noneMatch(p->p.getNodeId()==originatorId)){
//                    node.addNewNodeToLists(originatorId);
//                }
                node.setState(NodeState.DETECTING);
                node.processQuestion(originatorId, testNum, senderId);
            }
//            else{
//                node.sendDeadlockFreeMsg(originatorId, testNum, senderId);
//            }
        }else if(messageText.startsWith("ANSWER")){
//            dfmCount=0;

            if(node.getType() == NodeType.PASSIVE){
                System.out.println("Answer from " + senderId);
                node.processAnswer(originatorId,testNum, receiverId);
            }
        }
//        else if(messageText.startsWith("REJECT")){
//            if(node.getType() == NodeType.PASSIVE){
//                System.out.println("Answer from " + senderId);
//                node.processAnswer(originatorId,testNum, receiverId);
//            }
//        }
//        else if(messageText.startsWith("DFM")){
//            if (node.getType() == NodeType.PASSIVE) {
//                if(node.getListOfDependencies().contains(senderId)){
//                    dfmCount++;
//                }
//                if(dfmCount==node.getListOfDependencies().size()){
//                    System.out.println("Node " + node.getNodeId() + " is not deadlocked");
//                    dfmCount=0;
//                }
//            }
//        }
    }

    private void convertMessage(TextMessage message) throws JMSException {
        messageText = message.getText().split("\\|")[0];
        originatorId = Integer.parseInt(message.getText().split("\\|")[1]);
        testNum = Integer.parseInt(message.getText().split("\\|")[2]);
        senderId = Integer.parseInt(message.getText().split("\\|")[3]);
        receiverId = Integer.parseInt(message.getText().split("\\|")[4]);
    }
}
