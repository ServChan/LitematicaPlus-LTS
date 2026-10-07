package org.lts.litematicaplus.share;

public enum SyncPacket {
    REGISTER_METADATA("register_metadata"),
    CANCEL_SHARE("cancel_share"),
    REQUEST_LITEMATIC("request_download"),
    SEND_LITEMATIC("send_litematic"),
    RECEIVED_LITEMATIC("received_litematic"),
    FINISHED_LITEMATIC("finished_litematic"),
    CANCEL_LITEMATIC("cancel_litematic"),
    REMOVE_SYNCMATIC("remove_syncmatic"),
    REGISTER_VERSION("register_version"),
    CONFIRM_USER("confirm_user"),
    FEATURE_REQUEST("feature_request"),
    FEATURE("feature"),
    MODIFY("modify"),
    MODIFY_REQUEST("modify_request"),
    MODIFY_REQUEST_DENY("modify_request_deny"),
    MODIFY_REQUEST_ACCEPT("modify_request_accept"),
    MODIFY_FINISH("modify_finish"),
    MESSAGE("mesage");

    private final String id;

    SyncPacket(String path) {
        this.id = SyncCodec.NAMESPACE + ":" + path;
    }

    public String id() {
        return this.id;
    }

    public static SyncPacket byId(String id) {
        for (SyncPacket packet : values()) {
            if (packet.id.equals(id)) {
                return packet;
            }
        }
        return null;
    }
}
