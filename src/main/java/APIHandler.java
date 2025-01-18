import io.javalin.Javalin;

import java.util.List;

public class APIHandler {
    private int port = 7000;
    private Node myNode = null;
    private Javalin app = null;


    public APIHandler(Node myNode, int port) {
        this.myNode = myNode;
        this.port = port;
    }

    public APIHandler(Node myNode) {
        this(myNode, 7000);
    }


    public void start() {
        this.app = Javalin.create()
                .get("/get_dependencies", ctx -> {
                    System.out.println("Getting dependencies of this node");
                    List<Integer> result = myNode.getDependencies();
                    StringBuilder response = new StringBuilder();
                    for (Integer r : result) {
                        response.append("Node is dependent on ").append(r).append("\n");
                    }
                    ctx.result(response.toString());
                })
                .get("/get_state", ctx -> {
                    System.out.println("Getting state of node.");
                    ctx.result( myNode.getState() + "\n");
                })
                .get("/get_type", ctx -> {
                    System.out.println("Getting type of node.");
                    ctx.result( myNode.getType() + "\n");
                })
                .get("/get_count", ctx -> {
                    System.out.println("Getting current node count.");
                    ctx.result(myNode.getNodeIds().size() + "\n");
                })
                .get("/provoke_full_deadlock", ctx -> {
                    ctx.result("Provoking full deadlock." + "\n");
                    myNode.executeProvokeDeadlock();
                })
                .get("/provoke_single/{receiverId}", ctx -> {
                    ctx.result("Provoking single dependency." + "\n");
                    myNode.executeProvokeSingleDependency(Integer.parseInt(ctx.pathParam("receiverId")));
                })
                .get("/answer_single/{receiverId}", ctx -> {
                    ctx.result("Sending single message." + "\n");
                    myNode.executeAnswerSingle(Integer.parseInt(ctx.pathParam("receiverId")));
                })
                .get("/detect_deadlock", ctx -> {
                    ctx.result("Detecting deadlock." + "\n");
                    myNode.executeDetectDeadlock();
                })
                .get("/clear", ctx -> {
                    ctx.result("Clearing data after deadlock." + "\n");
                    myNode.executeClearDeadlock();
                })
                .get("/set_passive", ctx -> {
                    ctx.result("Setting passive node." + "\n");
                    myNode.setPassive();
                })
                .get("/set_active", ctx -> {
                    ctx.result("Trying to set node active." + "\n");
                    myNode.setActive();
                })
                .get("/exit", ctx -> {
                    ctx.result("Shutting down." + "\n");
                    myNode.shutdown();
                })
                .start(this.port);
    }
}
