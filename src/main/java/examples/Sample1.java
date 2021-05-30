package examples;

import com.petersamokhin.bots.sdk.clients.Group;
import com.petersamokhin.bots.sdk.keyboard.Button;
import com.petersamokhin.bots.sdk.keyboard.ButtonColor;
import com.petersamokhin.bots.sdk.keyboard.Keyboard;
import com.petersamokhin.bots.sdk.objects.Message;

public class Sample1 {

    public static void main(String[] args) {
        Group group = new Group(171509519 , "");
        Keyboard keys = Keyboard.of(new Button("sample", ButtonColor.DEFAULT), new Button("text", ButtonColor.NEGATIVE));
        //for buttons with ButtonColor.DEFAULT you can use String as argument
        //addButtons always add one new line and automatically group buttons in 4 per one line.
        //they will not add buttons to existing lines
        keys.addButtons("A", "B", "C", "D", "A1");
        //"A1" will be automatically moved to new line
        //then add it to response
        group.onSimpleTextMessage(message -> {
            System.out.println(message.getText());
            new Message()
                    .from(group)
                    .to(message.authorId())
                    .text(message.getText())
                    .keyboard(keys)
                    .send();
        });
        group.onDocMessage(message -> {
            System.out.println(message.getAttachments());
            System.out.println(message.getAttachments().getJSONObject(0).getJSONObject("doc").get("title"));
            System.out.println(message.getAttachments().getJSONObject(0).getJSONObject("doc").get("url"));
            new Message()
                    .from(group)
                    .to(message.authorId())
                    .text(message.getText())
                    .keyboard(keys)
                    .send();
        });

    }
}
