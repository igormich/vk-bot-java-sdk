package com.petersamokhin.bots.sdk.objects;

import com.petersamokhin.bots.sdk.callbacks.callbackapi.ExecuteCallback;
import com.petersamokhin.bots.sdk.clients.Client;
import com.petersamokhin.bots.sdk.utils.Connection;
import com.petersamokhin.bots.sdk.utils.Utils;
import com.petersamokhin.bots.sdk.utils.vkapi.API;
import okhttp3.MediaType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Message object for both (received and sent) messages
 */
public class Message {

    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    private Integer messageId, flags, peerId, timestamp, randomId, stickerId;
    private String text, accessToken, title;
    private String templateFileName;
    private API api;

    /**
     * Attachments in format of received event from longpoll server
     * More: <a href="https://vk.com/dev/using_longpoll_2">link</a>
     */
    private JSONObject attachmentsJO;

    /**
     * Attahments in format [photo62802565_456241137, photo111_111, doc100_500]
     */
    private String[] attachments, forwardedMessages;

    /**
     * Constructor for sent message
     */
    public Message() {
    }

    /**
     * Constructor for received message
     */
    public Message(Client client, Integer messageId, Integer flags, Integer peerId, Integer timestamp, String text, JSONObject attachments, Integer randomId) {

        setAccessToken(client.getAccessToken());
        setMessageId(messageId);
        setFlags(flags);
        setPeerId(peerId);
        setTimestamp(timestamp);
        setText(text);
        setAttachments(attachments);
        setRandomId(randomId);
        setTitle(attachments.getString("title"));

        api = client.api();
    }

    /**
     * Your client with id, access token
     */
    public Message from(Client client) {
        setAccessToken(client.getAccessToken());
        api = client.api();
        return this;
    }

    /**
     * ID of target dialog
     */
    public Message to(Integer peerId) {
        this.peerId = peerId;
        return this;
    }

    /**
     * ID of sticker, only for user tokens
     */
    public Message sticker(Integer id) {
        this.stickerId = id;
        return this;
    }

    /**
     * IDs of forwarded messages
     */
    public Message forwardedMessages(Object... ids) {

        String[] arr = new String[ids.length];

        for (int i = 0; i < ids.length; i++) {
            arr[i] = String.valueOf(ids[i]);
        }

        this.forwardedMessages = arr;

        return this;
    }

    /**
     * Message text
     */
    public Message text(Object text) {
        this.text = String.valueOf(text);
        return this;
    }

    /**
     * Message title (bold text)
     */
    public Message title(Object title) {
        this.title = String.valueOf(title);
        return this;
    }

    /**
     * Message attachments
     */
    public Message attachments(String... attachments) {

        if (attachments.length > 10)
            LOG.error("Trying to send message with illegal count of attachments: {} (>10)", attachments.length);
        else if (attachments.length == 1 && attachments[0].contains(",")) {
            this.attachments = attachments[0].split(",");
        } else {
            this.attachments = attachments;
        }
        return this;
    }

    /**
     * Message random_id
     */
    public Message randomId(Integer randomId) {
        this.randomId = randomId;
        return this;
    }

    /**
     * Attach photo to message
     *
     * @param photo Photo link: url, from disk or already uploaded to VK as photo{owner_id}_{id}
     */
    public Message photo(String photo) {

        boolean photoFromUrl = false;

        // Use already loaded photo
        if (Pattern.matches("photo-?\\d+_\\d+", photo) || Pattern.matches("/photo-?\\d+_\\d+", photo) || Pattern.matches("https?://vk\\.com/photo-?\\d+_\\d+", photo)) {

            photo = photo.replace("https://vk.com/", "").replace("/", "");
        }

        // Use file from disk or url
        if (photo.endsWith(".png") || photo.endsWith(".jpg") || photo.endsWith(".gif") || photo.endsWith(".jpeg")) {

            File template_photo;

            if (Pattern.matches("https?://.+", photo)) {
                try {
                    template_photo = new File("template_" + ((peerId != null) ? peerId : new Random().nextInt(Integer.MAX_VALUE)) + "." + FilenameUtils.getExtension(photo));
                    template_photo.createNewFile();
                    Files.setPosixFilePermissions(Paths.get(template_photo.getAbsolutePath()), PosixFilePermissions.fromString("rwxrwxrwx"));
                    FileUtils.copyURLToFile(new URL(photo), template_photo, 5000, 5000);

                    photoFromUrl = true;
                } catch (IOException ignored) {
                    LOG.error("IOException when downloading file {}, message: {}", photo, ignored.toString());
                    return this;
                }

            } else {

                // or else we use file from disc (i hope no one will use this on windows, i dont realy know if it will works, lol
                template_photo = new File(photo);
            }

            String uploadUrl;
            int maxAttempts = 5, i = 0;


            while (i < maxAttempts) {
                i++;

                // Getting of server for uploading the photo
                String getUploadServerQuery = "https://api.vk.com/method/photos.getMessagesUploadServer?access_token=" + accessToken + "&peer_id=" + this.peerId + "&v=5.67";
                JSONObject getUploadServerResponse = Connection.getRequestResponse(getUploadServerQuery);
                uploadUrl = getUploadServerResponse.has("response") ? getUploadServerResponse.getJSONObject("response").has("upload_url") ? getUploadServerResponse.getJSONObject("response").getString("upload_url") : null : null;


                // Some error
                if (uploadUrl == null) {
                    continue;
                }

                // Uploading the photo
                String uploadingOfPhotoResponseString = Connection.getFileUploadAnswerOfVK(uploadUrl, "photo", MediaType.parse("image/*"), template_photo);
                JSONObject uploadingOfPhotoResponse = new JSONObject();

                try {
                    uploadingOfPhotoResponse = new JSONObject(uploadingOfPhotoResponseString);
                } catch (JSONException ignored) {
                    LOG.error("Bad response: {}, error: {}", uploadingOfPhotoResponseString, ignored.toString());
                }

                // Getting necessary params
                String server, photo_param, hash;
                if (uploadingOfPhotoResponse.has("server") & uploadingOfPhotoResponse.has("photo") && uploadingOfPhotoResponse.has("hash")) {
                    server = "" + uploadingOfPhotoResponse.getInt("server");
                    photo_param = uploadingOfPhotoResponse.get("photo").toString();
                    hash = uploadingOfPhotoResponse.getString("hash");
                } else {
                    continue;
                }

                // Saving the photo
                String saveMessagesPhotoQuery = "https://api.vk.com/method/photos.saveMessagesPhoto?access_token=" + accessToken + "&v=5.67&server=" + server + "&photo=" + photo_param + "&hash=" + hash;
                JSONObject saveMessagesPhotoResponse = Connection.getRequestResponse(saveMessagesPhotoQuery);
                String photoAsAttach = saveMessagesPhotoResponse.has("response") ? "photo" + saveMessagesPhotoResponse.getJSONArray("response").getJSONObject(0).getInt("owner_id") + "_" + saveMessagesPhotoResponse.getJSONArray("response").getJSONObject(0).getInt("id") : "";

                if (photoAsAttach.length() < 2) {
                    continue;
                }

                if (photoFromUrl) {
                    try {
                        Files.delete(Paths.get(template_photo.getAbsolutePath()));
                    } catch (IOException ignored) {
                    }
                }

                if (Pattern.matches("photo-?\\d+_\\d+", photoAsAttach)) {
                    photo = photoAsAttach;
                    break;
                }
            }
        }

        this.attachments = this.attachments != null ? this.attachments : new String[]{};
        String[] attachmentsNew = new String[this.attachments.length + 1];
        attachmentsNew[attachmentsNew.length - 1] = photo;

        System.arraycopy(this.attachments, 0, attachmentsNew, 0, attachments.length);
        this.attachments = attachmentsNew;

        return this;
    }

    /**
     * Attach doc to message
     *
     * @param doc Doc link: url, from disk or already uploaded to VK as doc{owner_id}_{id}
     */
    public Message doc(String doc, String... type) {

        if (type.length > 0 && !type[0].contains("doc") && !type[0].contains("audio_message") && !type[0].contains("graffiti")) {
            LOG.error("Error when attaching doc to message: type should be 'doc', 'audio_message' or 'graffiti', your type is '{}'.", type[0]);

            if (type.length > 1)
                LOG.error("Unknown params when calling '.doc' method: please give link to doc (or path from disc, or doc<OWNER_ID>_<ID>) and type of it.");
        }

        boolean fileFromUrl = false;

        // Use already loaded doc
        if (Pattern.matches("doc-?\\d+_\\d+", doc) || Pattern.matches("/doc-?\\d+_\\d+", doc) || Pattern.matches("https?://vk\\.com/doc-?\\d+_\\d+", doc)) {

            doc = doc.replace("https://vk.com/", "").replace("/", "");
        } else

        // Use file from disk or url
        {
            File template_file;

            if (Pattern.matches("https?://.+", doc)) {
                int sizeOfFile = Utils.sizeOfFile(doc, "mbits");

                if (sizeOfFile > 10) {
                    LOG.error("Trying to upload file that is too big. URL is {} and size is {}", doc, sizeOfFile);
                    return this;
                }

                try {
                    String tmpName = doc.substring(doc.lastIndexOf('/') + 1, doc.length());
                    if (templateFileName != null && templateFileName.length() > 2) {
                        tmpName = templateFileName;
                    }
                    template_file = new File(tmpName);
                    template_file.createNewFile();
                    Files.setPosixFilePermissions(Paths.get(template_file.getAbsolutePath()), PosixFilePermissions.fromString("rwxrwxrwx"));
                    FileUtils.copyURLToFile(new URL(doc), template_file, 5000, 5000);

                    fileFromUrl = true;
                } catch (IOException ignored) {
                    LOG.error("IOException when downloading file {}, message: {}", doc, ignored.toString());
                    return this;
                }

            } else {

                // or else we use file from disc (i hope no one will use this on windows, i dont realy know if it will works, lol)
                template_file = new File(doc);
                if (!template_file.exists()) {
                    LOG.error("Trying to upload doc from disc, but file is not exists: {}", doc);
                    return this;
                }
            }

            String uploadUrl;
            int maxAttempts = 5, i = 0;
            boolean good = false;

            while (i < maxAttempts) {
                i++;

                // Getting of server for uploading the photo
                String getUploadServerQuery = "https://api.vk.com/method/docs.getMessagesUploadServer?" + (type.length > 0 ? "type=" + type[0] : "") + "&access_token=" + accessToken + "&peer_id=" + this.peerId + "&v=5.67";
                JSONObject getUploadServerResponse = Connection.getRequestResponse(getUploadServerQuery);
                uploadUrl = getUploadServerResponse.has("response") ? getUploadServerResponse.getJSONObject("response").has("upload_url") ? getUploadServerResponse.getJSONObject("response").getString("upload_url") : null : null;

                // Some error
                if (uploadUrl == null) {

                    LOG.error("Error when trying to get upload server for doc, response: {}", getUploadServerResponse);

                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                // Uploading the photo
                String uploadingOfDocResponseString = Connection.getFileUploadAnswerOfVK(uploadUrl, "file", MediaType.parse("image/*"), template_file);
                uploadingOfDocResponseString = (uploadingOfDocResponseString != null && uploadingOfDocResponseString.length() > 2) ? uploadingOfDocResponseString : "{}";
                JSONObject uploadingOfDocResponse = new JSONObject(uploadingOfDocResponseString);

                // Getting necessary params
                String file;
                if (uploadingOfDocResponse.has("file")) {
                    file = "" + uploadingOfDocResponse.getString("file");
                } else {

                    LOG.error("Error when uploading doc, no file field in response: {}", uploadingOfDocResponse);

                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                // Saving the doc
                String saveMessagesDocQuery = "https://api.vk.com/method/docs.save?access_token=" + accessToken + "&v=5.67&file=" + file;
                JSONObject saveMessagesDocResponse = Connection.getRequestResponse(saveMessagesDocQuery);
                String docAsAttach = saveMessagesDocResponse.has("response") ? "doc" + saveMessagesDocResponse.getJSONArray("response").getJSONObject(0).getInt("owner_id") + "_" + saveMessagesDocResponse.getJSONArray("response").getJSONObject(0).getInt("id") : "";

                if (docAsAttach.length() < 2) {

                    LOG.error("Error when uploading doc, bad doc: {}", saveMessagesDocResponse);

                    try {
                        Thread.sleep(400);
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                if (fileFromUrl) {
                    try {
                        Files.delete(Paths.get(template_file.getAbsolutePath()));
                    } catch (IOException ignored) {
                    }
                }

                if (Pattern.matches("doc-?\\d+_\\d+", docAsAttach)) {
                    doc = docAsAttach;
                    good = true;
                    break;
                }
            }

            if (!good) {
                return this;
            }
        }

        this.attachments = this.attachments != null ? this.attachments : new String[]{};
        String[] attachmentsNew = new String[this.attachments.length + 1];
        attachmentsNew[attachmentsNew.length - 1] = doc;

        System.arraycopy(this.attachments, 0, attachmentsNew, 0, attachments.length);
        this.attachments = attachmentsNew;

        return this;
    }

    public Message templateFileName(String name) {
        this.templateFileName = name;

        return this;
    }

    /**
     * Send the message
     *
     * @param callback will be called with response object
     */
    public void send(ExecuteCallback... callback) {

        text = (text != null && text.length() > 0) ? text : "";
        title = (title != null && title.length() > 0) ? title : "";

        randomId = randomId != null && randomId > 0 ? randomId : 0;
        peerId = peerId != null ? peerId : -142409596;
        attachments = attachments != null && attachments.length > 0 ? attachments : new String[]{};
        forwardedMessages = forwardedMessages != null && forwardedMessages.length > 0 ? forwardedMessages : new String[]{};
        stickerId = stickerId != null && stickerId > 0 ? stickerId : 0;

        JSONObject params = new JSONObject();

        params.put("message", text);
        if (title != null && title.length() > 0) params.put("title", title);
        if (randomId != null && randomId > 0) params.put("random_id", randomId);
        params.put("peer_id", peerId);
        if (attachments.length > 0) params.put("attachment", String.join(",", attachments));
        if (forwardedMessages.length > 0) params.put("forward_messages", String.join(",", forwardedMessages));
        if (stickerId != null && stickerId > 0) params.put("sticker_id", stickerId);

        api.call("messages.send", params, response -> {
            if (callback.length > 0) {
                callback[0].onResponse(response);
            }
            if (!(response instanceof Integer)) {
                LOG.error("Message not sent: {}", response);
            }
        });
    }

    /**
     * Get the type of message
     */
    public String messageType() {

        if (isVoiceMessage()) {
            return "voiceMessage";
        } else if (isStickerMessage()) {
            return "stickerMessage";
        } else if (isGifMessage()) {
            return "gifMessage";
        } else if (isAudioMessage()) {
            return "audioMessage";
        } else if (isVideoMessage()) {
            return "videoMessage";
        } else if (isDocMessage()) {
            return "docMessage";
        } else if (isWallMessage()) {
            return "wallMessage";
        } else if (isPhotoMessage()) {
            return "photoMessage";
        } else if (isLinkMessage()) {
            return "linkMessage";
        } else if (isSimpleTextMessage()) {
            return "simpleTextMessage";
        } else return "error";
    }

    /**
     * @return true if message has forwarded messages
     */
    public boolean hasFwds() {
        boolean answer = false;

        if (attachmentsJO.has("fwd"))
            answer = true;

        return answer;
    }

    /**
     * @return array of forwarded messages or []
     */
    public JSONArray getForwardedMessages() {
        if (hasFwds()) {
            JSONObject response = api.callSync("messages.getById", "message_ids", getMessageId());

            if (response.has("response") && response.getJSONObject("response").getJSONArray("items").getJSONObject(0).has("fwd_messages")) {
                return response.getJSONObject("response").getJSONArray("items").getJSONObject(0).getJSONArray("fwd_messages");
            }
        }

        return new JSONArray();
    }

    /**
     * Get attachments from message
     */
    public JSONArray getAttachments() {

        JSONObject response = api.callSync("messages.getById", "message_ids", getMessageId());

        if (response.has("response") && response.getJSONObject("response").getJSONArray("items").getJSONObject(0).has("attachments"))
            return response.getJSONObject("response").getJSONArray("items").getJSONObject(0).getJSONArray("attachments");

        return new JSONArray();
    }

    /*
     * Priority: voice, sticker, gif, ... , simple text
     */
    public boolean isPhotoMessage() {
        return getCountOfAttachmentsByType().get("photo") > 0;
    }

    public boolean isSimpleTextMessage() {
        return getCountOfAttachmentsByType().get("summary") == 0;
    }

    public boolean isVoiceMessage() {
        return getCountOfAttachmentsByType().get("voice") > 0;
    }

    public boolean isAudioMessage() {
        return getCountOfAttachmentsByType().get("audio") > 0;
    }

    public boolean isVideoMessage() {
        return getCountOfAttachmentsByType().get("video") > 0;
    }

    public boolean isDocMessage() {
        return getCountOfAttachmentsByType().get("doc") > 0;
    }

    public boolean isWallMessage() {
        return getCountOfAttachmentsByType().get("wall") > 0;
    }

    public boolean isStickerMessage() {
        return getCountOfAttachmentsByType().get("sticker") > 0;
    }

    public boolean isLinkMessage() {
        return getCountOfAttachmentsByType().get("link") > 0;
    }

    public boolean isGifMessage() {
        JSONArray attachments = getAttachments();

        for (int i = 0; i < attachments.length(); i++) {
            if (attachments.getJSONObject(i).has("type") && attachments.getJSONObject(i).getJSONObject(attachments.getJSONObject(i).getString("type")).has("type") && attachments.getJSONObject(i).getJSONObject(attachments.getJSONObject(i).getString("type")).getInt("type") == 3)
                return true;
        }

        return false;
    }

    // Getters and setters for handling new message

    public Map<String, Integer> getCountOfAttachmentsByType() {

        int photo = 0, video = 0, audio = 0, doc = 0, wall = 0, link = 0;

        Map<String, Integer> answer = new HashMap<String, Integer>() {{
            put("photo", 0);
            put("video", 0);
            put("audio", 0);
            put("doc", 0);
            put("wall", 0);
            put("sticker", 0);
            put("link", 0);
            put("voice", 0);
            put("summary", 0);
        }};

        if (attachmentsJO.toString().contains("sticker")) {
            answer.put("sticker", 1);
            answer.put("summary", 1);
            return answer;
        } else {
            if (attachmentsJO.toString().contains("audiomsg")) {
                answer.put("voice", 1);
                answer.put("summary", 1);
                return answer;
            } else {
                for (String key : attachmentsJO.keySet()) {
                    if (key.startsWith("attach") && key.endsWith("type")) {

                        String value = attachmentsJO.getString(key);
                        switch (value) {

                            case "photo": {
                                answer.put(value, ++photo);
                                break;
                            }
                            case "video": {
                                answer.put(value, ++video);
                                break;
                            }
                            case "audio": {
                                answer.put(value, ++audio);
                                break;
                            }
                            case "doc": {
                                answer.put(value, ++doc);
                                break;
                            }
                            case "wall": {
                                answer.put(value, ++wall);
                                break;
                            }
                            case "link": {
                                answer.put(value, ++link);
                                break;
                            }
                        }
                    }

                }
            }
        }

        int summary = 0;
        for (String key : answer.keySet()) {
            if (answer.get(key) > 0)
                summary++;
        }
        answer.put("summary", summary);

        return answer;
    }

    public Integer getMessageId() {
        return messageId;
    }

    private void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public Integer getFlags() {
        return flags;
    }

    private void setFlags(Integer flags) {
        this.flags = flags;
    }

    public Integer authorId() {
        return peerId;
    }

    private void setPeerId(Integer peerId) {
        this.peerId = peerId;
    }

    public Integer getTimestamp() {
        return timestamp;
    }

    private void setTimestamp(Integer timestamp) {
        this.timestamp = timestamp;
    }

    public String getText() {
        return text;
    }

    private void setText(String text) {
        this.text = text;
    }

    public JSONArray getPhotos() {
        JSONArray attachments = getAttachments();
        JSONArray answer = new JSONArray();

        for (int i = 0; i < attachments.length(); i++) {
            if (attachments.getJSONObject(i).getString("type").contains("photo"))
                answer.put(attachments.getJSONObject(i).getJSONObject("photo"));
        }

        return answer;
    }

    /**
     * @param photos JSONArray with photo objects
     * @return URL of biggest image file
     */
    public String getBiggestPhotoUrl(JSONArray photos) {

        String currentBiggestPhoto = null;

        for (int i = 0; i < photos.length(); i++) {
            if (photos.getJSONObject(i).has("photo_1280"))
                currentBiggestPhoto = photos.getJSONObject(i).getString("photo_1280");
            else if (photos.getJSONObject(i).has("photo_807"))
                currentBiggestPhoto = photos.getJSONObject(i).getString("photo_807");
            else if (photos.getJSONObject(i).has("photo_604"))
                currentBiggestPhoto = photos.getJSONObject(i).getString("photo_604");
            else if (photos.getJSONObject(i).has("photo_130"))
                currentBiggestPhoto = photos.getJSONObject(i).getString("photo_130");
            else if (photos.getJSONObject(i).has("photo_75"))
                currentBiggestPhoto = photos.getJSONObject(i).getString("photo_75");
        }

        return currentBiggestPhoto;
    }

    public JSONObject getVoiceMessage() {

        JSONArray attachments = getAttachments();
        JSONObject answer = new JSONObject();

        for (int i = 0; i < attachments.length(); i++) {
            if (attachments.getJSONObject(i).getString("type").contains("doc") && attachments.getJSONObject(i).getJSONObject("doc").toString().contains("waveform"))
                answer = attachments.getJSONObject(i).getJSONObject("doc");
        }

        return answer;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    private void setAttachments(JSONObject attachments) {

        this.attachmentsJO = attachments;
    }

    public Integer getRandomId() {
        return randomId;
    }

    private void setRandomId(Integer randomId) {
        this.randomId = randomId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    private String[] getForwardedMessagesIds() {

        if (attachmentsJO.has("fwd")) {
            return attachmentsJO.getString("fwd").split(",");
        }

        return new String[]{};
    }

    @Override
    public String toString() {
        return '{' +
                "\"message_id\":" + messageId +
                ",\"flags\":" + flags +
                ",\"peer_id\":" + peerId +
                ",\"timestamp\":" + timestamp +
                ",\"random_id\":" + randomId +
                ",\"text\":\"" + text + '\"' +
                ",\"attachments\":" + attachmentsJO.toString() +
                '}';
    }
}
