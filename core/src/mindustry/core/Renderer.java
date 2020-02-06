package mindustry.core;

import arc.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.gl.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.core.GameState.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class Renderer implements ApplicationListener{
    public final BlockRenderer blocks = new BlockRenderer();
    public final MinimapRenderer minimap = new MinimapRenderer();
    public final OverlayRenderer overlays = new OverlayRenderer();
    public final LightRenderer lights = new LightRenderer();
    public final Pixelator pixelator = new Pixelator();

    public FrameBuffer effectBuffer = new FrameBuffer(2, 2);
    private Bloom bloom;
    private Color clearColor;
    private float targetscale = Scl.scl(4);
    private float camerascale = targetscale;
    private float landscale = 0f, landTime;
    private float minZoomScl = Scl.scl(0.01f);
    private Rect rect = new Rect(), rect2 = new Rect();
    private float shakeIntensity, shaketime;

    public Renderer(){
        camera = new Camera();
        Shaders.init();

        clearColor = new Color(0f, 0f, 0f, 1f);
    }

    public void shake(float intensity, float duration){
        shakeIntensity = Math.max(shakeIntensity, intensity);
        shaketime = Math.max(shaketime, duration);
    }

    @Override
    public void init(){
        if(settings.getBool("bloom")){
            setupBloom();
        }
    }

    @Override
    public void update(){
        Color.white.set(1f, 1f, 1f, 1f);

        camerascale = Mathf.lerpDelta(camerascale, targetscale, 0.1f);

        if(landTime > 0){
            landTime -= Time.delta();
            landscale = Interpolation.pow5In.apply(minZoomScl, Scl.scl(4f), 1f - landTime / Fx.coreLand.lifetime);
            camerascale = landscale;
        }

        camera.width = graphics.getWidth() / camerascale;
        camera.height = graphics.getHeight() / camerascale;

        if(state.is(State.menu)){
            landTime = 0f;
            graphics.clear(Color.black);
        }else{
            Vec2 position = Tmp.v3.set(player);

            //TODO camera controls should be in input
            /*
            if(player.dead()){
                Tilec core = player.closestCore();
                if(core != null){
                    if(player.spawner == null){
                        camera.position.lerpDelta(core.x, core.y, 0.08f);
                    }else{
                        camera.position.lerpDelta(position, 0.08f);
                    }
                }
            }else if(control.input instanceof DesktopInput && !state.isPaused()){
                camera.position.lerpDelta(position, 0.08f);
            }*/

            updateShake(0.75f);
            if(pixelator.enabled()){
                pixelator.drawPixelate();
            }else{
                draw();
            }
        }
    }

    public float landScale(){
        return landTime > 0 ? landscale : 1f;
    }

    @Override
    public void dispose(){
        minimap.dispose();
        effectBuffer.dispose();
        blocks.dispose();
        if(bloom != null){
            bloom.dispose();
            bloom = null;
        }
        Events.fire(new DisposeEvent());
    }

    @Override
    public void resize(int width, int height){
        if(settings.getBool("bloom")){
            setupBloom();
        }
    }

    @Override
    public void resume(){
        if(settings.getBool("bloom") && bloom != null){
            bloom.resume();
        }
    }

    void setupBloom(){
        try{
            if(bloom != null){
                bloom.dispose();
                bloom = null;
            }
            bloom = new Bloom(true);
            bloom.setClearColor(0f, 0f, 0f, 0f);
        }catch(Exception e){
            e.printStackTrace();
            settings.put("bloom", false);
            settings.save();
            ui.showErrorMessage("$error.bloom");
        }
    }

    public void toggleBloom(boolean enabled){
        if(enabled){
            if(bloom == null){
                setupBloom();
            }
        }else{
            if(bloom != null){
                bloom.dispose();
                bloom = null;
            }
        }
    }

    void updateShake(float scale){
        if(shaketime > 0){
            float intensity = shakeIntensity * (settings.getInt("screenshake", 4) / 4f) * scale;
            camera.position.add(Mathf.range(intensity), Mathf.range(intensity));
            shakeIntensity -= 0.25f * Time.delta();
            shaketime -= Time.delta();
            shakeIntensity = Mathf.clamp(shakeIntensity, 0f, 100f);
        }else{
            shakeIntensity = 0f;
        }
    }

    public void draw(){
        camera.update();

        if(Float.isNaN(camera.position.x) || Float.isNaN(camera.position.y)){
            camera.position.set(player);
        }

        graphics.clear(clearColor);

        if(!graphics.isHidden() && (Core.settings.getBool("animatedwater") || Core.settings.getBool("animatedshields")) && (effectBuffer.getWidth() != graphics.getWidth() || effectBuffer.getHeight() != graphics.getHeight())){
            effectBuffer.resize(graphics.getWidth(), graphics.getHeight());
        }

        Draw.proj(camera.projection());

        blocks.floor.drawFloor();

        render(RenderLayer.floor);

        blocks.processBlocks();
        blocks.drawShadows();
        Draw.color();

        blocks.floor.beginDraw();
        blocks.floor.drawLayer(CacheLayer.walls);
        blocks.floor.endDraw();

        blocks.drawBlocks(Layer.block);
        blocks.drawFog();

        blocks.drawDestroyed();

        Draw.shader(Shaders.blockbuild, true);
        blocks.drawBlocks(Layer.placement);
        Draw.shader();

        blocks.drawBlocks(Layer.overlay);

        render(RenderLayer.groundShadows);
        render(RenderLayer.ground);

        blocks.drawBlocks(Layer.turret);

        render(RenderLayer.flyingShadows);

        blocks.drawBlocks(Layer.power);
        blocks.drawBlocks(Layer.lights);

        render(RenderLayer.flying);

        Draw.flush();
        if(bloom != null && !pixelator.enabled()){
            bloom.capture();
        }

        render(RenderLayer.bullets);
        render(RenderLayer.effects);

        Draw.flush();
        if(bloom != null && !pixelator.enabled()){
            bloom.render();
        }

        overlays.drawBottom();
        if(player.isBuilder()){
            player.builder().drawBuildRequests();
        }

        overlays.drawTop();

        //TODO should use (draw)
        Groups.player.each(p -> !p.dead(), Playerc::drawName);

        if(state.rules.lighting){
            lights.draw();
        }

        drawLanding();

        Draw.color();
        Draw.flush();
    }

    private void render(RenderLayer layer){

    }

    private void drawLanding(){
        if(landTime > 0 && player.closestCore() != null){
            float fract = landTime / Fx.coreLand.lifetime;
            Tilec entity = player.closestCore();

            TextureRegion reg = entity.block().icon(Cicon.full);
            float scl = Scl.scl(4f) / camerascale;
            float s = reg.getWidth() * Draw.scl * scl * 4f * fract;

            Draw.color(Pal.lightTrail);
            Draw.rect("circle-shadow", entity.getX(), entity.getY(), s, s);

            Angles.randLenVectors(1, (1f- fract), 100, 1000f * scl * (1f-fract), (x, y, fin, fout) -> {
                Lines.stroke(scl * fin);
                Lines.lineAngle(entity.getX() + x, entity.getY() + y, Mathf.angle(x, y), (fin * 20 + 1f) * scl);
            });

            Draw.color();
            Draw.mixcol(Color.white, fract);
            Draw.rect(reg, entity.getX(), entity.getY(), reg.getWidth() * Draw.scl * scl, reg.getHeight() * Draw.scl * scl, fract * 135f);

            Draw.reset();
        }
    }
/*
    public void draw(){
        camera.update();

        if(Float.isNaN(camera.position.x) || Float.isNaN(camera.position.y)){
            camera.position.x = player.x;
            camera.position.y = player.y;
        }

        graphics.clear(clearColor);

        if(!graphics.isHidden() && (Core.settings.getBool("animatedwater") || Core.settings.getBool("animatedshields")) && (effectBuffer.getWidth() != graphics.getWidth() || effectBuffer.getHeight() != graphics.getHeight())){
            effectBuffer.resize(graphics.getWidth(), graphics.getHeight());
        }

        Draw.proj(camera.projection());

        blocks.floor.drawFloor();

        groundEffectGroup.draw(e -> e instanceof BelowLiquidTrait);
        puddleGroup.draw();
        groundEffectGroup.draw(e -> !(e instanceof BelowLiquidTrait));

        blocks.processBlocks();

        blocks.drawShadows();
        Draw.color();

        blocks.floor.beginDraw();
        blocks.floor.drawLayer(CacheLayer.walls);
        blocks.floor.endDraw();

        blocks.drawBlocks(Layer.block);
        blocks.drawFog();

        blocks.drawDestroyed();

        Draw.shader(Shaders.blockbuild, true);
        blocks.drawBlocks(Layer.placement);
        Draw.shader();

        blocks.drawBlocks(Layer.overlay);

        drawGroundShadows();

        drawAllTeams(false);

        blocks.drawBlocks(Layer.turret);

        drawFlyerShadows();

        blocks.drawBlocks(Layer.power);
        blocks.drawBlocks(Layer.lights);

        drawAllTeams(true);

        Draw.flush();
        if(bloom != null && !pixelator.enabled()){
            bloom.capture();
        }

        bulletGroup.draw();
        effectGroup.draw();

        Draw.flush();
        if(bloom != null && !pixelator.enabled()){
            bloom.render();
        }

        overlays.drawBottom();
        Groups.player.draw(p -> p.isLocal, Playerc::drawBuildRequests);

        if(shieldGroup.countInBounds() > 0){
            if(settings.getBool("animatedshields") && Shaders.shield != null){
                Draw.flush();
                effectBuffer.begin();
                graphics.clear(Color.clear);
                shieldGroup.draw();
                shieldGroup.draw(shield -> true, ShieldEntity::drawOver);
                Draw.flush();
                effectBuffer.end();
                Draw.shader(Shaders.shield);
                Draw.color(Pal.accent);
                Draw.rect(Draw.wrap(effectBuffer.getTexture()), camera.position.x, camera.position.y, camera.width, -camera.height);
                Draw.color();
                Draw.shader();
            }else{
                shieldGroup.draw(shield -> true, ShieldEntity::drawSimple);
            }
        }

        overlays.drawTop();

        Groups.player.draw(p -> !p.isDead(), Playerc::drawName);

        if(state.rules.lighting){
            lights.draw();
        }

        drawLanding();

        Draw.color();
        Draw.flush();
    }

    private void drawLanding(){
        if(landTime > 0 && player.closestCore() != null){
            float fract = landTime / Fx.coreLand.lifetime;
            Tilec entity = player.closestCore();

            TextureRegion reg = entity.block.icon(Cicon.full);
            float scl = Scl.scl(4f) / camerascale;
            float s = reg.getWidth() * Draw.scl * scl * 4f * fract;

            Draw.color(Pal.lightTrail);
            Draw.rect("circle-shadow", entity.getX(), entity.getY(), s, s);

            Angles.randLenVectors(1, (1f- fract), 100, 1000f * scl * (1f-fract), (x, y, fin, fout) -> {
                Lines.stroke(scl * fin);
                Lines.lineAngle(entity.getX() + x, entity.getY() + y, Mathf.angle(x, y), (fin * 20 + 1f) * scl);
            });

            Draw.color();
            Draw.mixcol(Color.white, fract);
            Draw.rect(reg, entity.getX(), entity.getY(), reg.getWidth() * Draw.scl * scl, reg.getHeight() * Draw.scl * scl, fract * 135f);

            Draw.reset();
        }
    }

    private void drawGroundShadows(){
        Draw.color(0, 0, 0, 0.4f);
        float rad = 1.6f;

        Cons<Unitc> draw = u -> {
            float size = Math.max(u.getIconRegion().getWidth(), u.getIconRegion().getHeight()) * Draw.scl;
            Draw.rect("circle-shadow", u.x, u.y, size * rad, size * rad);
        };

        Groups.unit.draw(unit -> !unit.isDead(), draw::get);

        if(!Groups.player.isEmpty()){
            Groups.player.draw(unit -> !unit.isDead(), draw::get);
        }

        Draw.color();
    }

    private void drawFlyerShadows(){
        float trnsX = -12, trnsY = -13;
        Draw.color(0, 0, 0, 0.22f);

        Groups.unit.draw(unit -> unit.isFlying() && !unit.isDead(), baseUnit -> baseUnit.drawShadow(trnsX, trnsY));
        Groups.player.draw(unit -> unit.isFlying() && !unit.isDead(), player -> player.drawShadow(trnsX, trnsY));

        Draw.color();
    }

    private void drawAllTeams(boolean flying){
        Groups.unit.draw(u -> u.isFlying() == flying && !u.isDead(), Unitc::drawUnder);
        Groups.player.draw(p -> p.isFlying() == flying && !p.isDead(), Unitc::drawUnder);

        Groups.unit.draw(u -> u.isFlying() == flying && !u.isDead(), Unitc::drawAll);
        Groups.player.draw(p -> p.isFlying() == flying, Unitc::drawAll);

        Groups.unit.draw(u -> u.isFlying() == flying && !u.isDead(), Unitc::drawOver);
        Groups.player.draw(p -> p.isFlying() == flying, Unitc::drawOver);
    }*/

    public void scaleCamera(float amount){
        targetscale += amount;
        clampScale();
    }

    public void clampScale(){
        float s = Scl.scl(1f);
        targetscale = Mathf.clamp(targetscale, s * 1.5f, Math.round(s * 6));
    }

    public float getScale(){
        return targetscale;
    }

    public void setScale(float scl){
        targetscale = scl;
        clampScale();
    }

    public void zoomIn(float duration){
        landscale = minZoomScl;
        landTime = duration;
    }

    public void takeMapScreenshot(){
        //TODO uncomment
        /*
        drawGroundShadows();

        int w = world.width() * tilesize, h = world.height() * tilesize;
        int memory = w * h * 4 / 1024 / 1024;

        if(memory >= 65){
            ui.showInfo("$screenshot.invalid");
            return;
        }

        boolean hadShields = Core.settings.getBool("animatedshields");
        boolean hadWater = Core.settings.getBool("animatedwater");
        Core.settings.put("animatedwater", false);
        Core.settings.put("animatedshields", false);

        FrameBuffer buffer = new FrameBuffer(w, h);

        float vpW = camera.width, vpH = camera.height, px = camera.position.x, py = camera.position.y;
        disableUI = true;
        camera.width = w;
        camera.height = h;
        camera.position.x = w / 2f + tilesize / 2f;
        camera.position.y = h / 2f + tilesize / 2f;
        Draw.flush();
        buffer.begin();
        draw();
        Draw.flush();
        buffer.end();
        disableUI = false;
        camera.width = vpW;
        camera.height = vpH;
        camera.position.set(px, py);
        buffer.begin();
        byte[] lines = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        for(int i = 0; i < lines.length; i += 4){
            lines[i + 3] = (byte)255;
        }
        buffer.end();
        Pixmap fullPixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(lines, 0, fullPixmap.getPixels(), lines.length);
        Fi file = screenshotDirectory.child("screenshot-" + Time.millis() + ".png");
        PixmapIO.writePNG(file, fullPixmap);
        fullPixmap.dispose();
        ui.showInfoFade(Core.bundle.format("screenshot", file.toString()));

        buffer.dispose();

        Core.settings.put("animatedwater", hadWater);
        Core.settings.put("animatedshields", hadShields);*/
    }

}