package com.petersamokhin.bots.sdk.clients;

import com.petersamokhin.bots.sdk.callbacks.Callback;
import com.petersamokhin.bots.sdk.utils.Utils;
import com.petersamokhin.bots.sdk.utils.web.MultipartUtility;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Group client, that contains important methods to work with groups
 */
public class Group extends Client {

    private static final Logger LOG = LoggerFactory.getLogger(Group.class);

    /**
     * Default constructor
     *
     * @param id           User or group id
     * @param access_token Access token key
     */
    public Group(Integer id, String access_token) {
        super(id, access_token);
    }

    /**
     * Default constructor
     *
     * @param access_token Access token key
     */
    public Group(String access_token) {
        super(access_token);
    }

    /**
     * Upload group cover by file from url or from disk
     */
    public void uploadCover(String cover, Callback<Object> callback) {

        if (this.getId() == null || this.getId() == 0) {
            LOG.error("Please, provide group_id when initialising the client, because it's impossible to upload cover to group not knowing it id.");
            return;
        }

        byte[] bytes;

        File coverFile = new File(cover);
        if (coverFile.exists()) {
            try {
                bytes = Utils.toByteArray(coverFile.toURI().toURL());
            } catch (IOException ignored) {
                LOG.error("Cover file was exists, but IOException occured: {}", ignored.toString());
                return;
            }
        } else {
            URL coverUrl;
            try {
                coverUrl = new URL(cover);
                bytes = Utils.toByteArray(coverUrl);
            } catch (IOException ignored) {
                LOG.error("Bad string was proviced to uploadCocver method: path to file or url was expected, but got this: {}, error: {}", cover, ignored.toString());
                return;
            }
        }

        updateCoverByFile(bytes, callback);
    }

    /**
     * Updating cover by bytes (of file or url)
     *
     * @param bytes    bytes[]
     * @param callback response will return to callback
     */
    private void updateCoverByFile(byte[] bytes, Callback<Object>... callback) {

        JSONObject params_getUploadServer = new JSONObject()
                .put("group_id", getId())
                .put("crop_x", 0)
                .put("crop_y", 0)
                .put("crop_x2", 1590)
                .put("crop_y2", 400);

        api().call("photos.getOwnerCoverPhotoUploadServer", params_getUploadServer, response -> {

            String uploadUrl = new JSONObject(response.toString()).getString("upload_url");

            MultipartUtility multipartUtility = new MultipartUtility(uploadUrl);
            multipartUtility.addBytesPart("photo", "photo.png", bytes);
            String coverUploadedResponseString = multipartUtility.finish();

            coverUploadedResponseString = (coverUploadedResponseString != null && coverUploadedResponseString.length() > 2) ? coverUploadedResponseString : "{}";

            JSONObject coverUploadedResponse = new JSONObject(coverUploadedResponseString);

            if (coverUploadedResponse.has("hash") && coverUploadedResponse.has("photo")) {

                String hash_field = coverUploadedResponse.getString("hash");
                String photo_field = coverUploadedResponse.getString("photo");

                JSONObject params_saveCover = new JSONObject()
                        .put("hash", hash_field)
                        .put("photo", photo_field);

                boolean sync = true; // vk, please fix `execute` method!
                if (sync) {
                    JSONObject responseS = new JSONObject(api().callSync("photos.saveOwnerCoverPhoto", params_saveCover));
                    System.out.println("params is " + params_saveCover);
                    if (responseS.toString().length() < 10 || responseS.toString().contains("error")) {
                        LOG.error("Some error occured, cover not uploaded: {}", responseS);
                    }
                    if (callback.length > 0)
                        callback[0].onResult(responseS);
                } else {
                    api().call("photos.saveOwnerCoverPhoto", params_saveCover, response1 -> {

                        if (response1.toString().length() < 10 || response1.toString().contains("error")) {
                            LOG.error("Some error occured, cover not uploaded: {}", response1);
                        }
                        if (callback.length > 0) {
                            callback[0].onResult(response1);
                        }
                    });
                }
            } else {
                LOG.error("Error occured when uploading cover: no 'photo' or 'hash' param in response {}", coverUploadedResponse);
                if (callback.length > 0)
                    callback[0].onResult("false");
            }
        });
    }
}