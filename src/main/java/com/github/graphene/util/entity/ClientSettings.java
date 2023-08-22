package com.github.graphene.util.entity;

import com.github.retrooper.packetevents.protocol.player.HumanoidArm;
import com.github.retrooper.packetevents.protocol.player.SkinSection;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSettings;

import java.util.Set;

public class ClientSettings {
    private String locale;
    private int viewDistance;
    private SkinSection skinSection;
    private WrapperPlayClientSettings.ChatVisibility chatMode;
    private HumanoidArm mainHand;

    public ClientSettings(String locale, int viewDistance, SkinSection skinSection, WrapperPlayClientSettings.ChatVisibility chatMode, HumanoidArm mainHand) {
        this.locale = locale;
        this.viewDistance = viewDistance;
        this.skinSection = skinSection;
        this.chatMode = chatMode;
        this.mainHand = mainHand;
    }

    public ClientSettings(WrapperPlayClientSettings eventWrapper) {
        this.locale = eventWrapper.getLocale();
        this.viewDistance = eventWrapper.getViewDistance();
        this.skinSection = eventWrapper.getVisibleSkinSection();
        this.chatMode = eventWrapper.getVisibility();
        this.mainHand = eventWrapper.getMainHand();
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
    }

    public SkinSection getVisibleSkinSection() {
        return skinSection;
    }

    public void setVisibleSkinSection(SkinSection skinSection) {
        this.skinSection = skinSection;
    }

    public WrapperPlayClientSettings.ChatVisibility getChatVisibility() {
        return chatMode;
    }

    public void setChatVisibility(WrapperPlayClientSettings.ChatVisibility chatMode) {
        this.chatMode = chatMode;
    }

    public HumanoidArm getMainHand() {
        return mainHand;
    }

    public void setMainHand(HumanoidArm mainHand) {
        this.mainHand = mainHand;
    }
}
