package dev.jevcraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public final class TakeoverScreen extends Screen {
    private final String initial; private final Consumer<String> submit; private final Runnable stop; private EditBox input;
    public TakeoverScreen(String initial, Consumer<String> submit, Runnable stop) { super(Component.literal("JevCraft Takeover")); this.initial=initial; this.submit=submit; this.stop=stop; }
    @Override protected void init() {
        int left=width/2-150, top=height-78; input=new EditBox(font,left,top,300,20,Component.literal("Instruction"));
        input.setMaxLength(512); input.setValue(initial); addRenderableWidget(input); setInitialFocus(input);
        addRenderableWidget(Button.builder(Component.literal("Run"), b->{submit.accept(input.getValue()); onClose();}).bounds(left,top+24,145,20).build());
        addRenderableWidget(Button.builder(Component.literal("STOP"), b->{stop.run(); onClose();}).bounds(left+155,top+24,145,20).build());
    }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        graphics.fill(width/2-160,height-94,width/2+160,height-20,0xCC101018);
        graphics.drawString(font,TakeoverRuntime.active()?"Active: "+TakeoverRuntime.goal():"Takeover stopped",width/2-150,height-92,0xFFFFFF);
        graphics.drawString(font,TakeoverRuntime.status(),width/2-150,height-81,0xAAAAFF); super.render(graphics,mouseX,mouseY,partialTick);
    }
    @Override public boolean isPauseScreen(){return false;}
}
