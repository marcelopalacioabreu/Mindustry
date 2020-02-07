package mindustry.entities.def;

import arc.math.*;
import mindustry.annotations.Annotations.*;
import mindustry.gen.*;
import mindustry.type.*;

@Component
abstract class ItemsComp implements Posc{
    @ReadOnly ItemStack stack = new ItemStack();

    abstract int itemCapacity();

    @Override
    public void update(){
        stack.amount = Mathf.clamp(stack.amount, 0, itemCapacity());
    }

    Item item(){
        return stack.item;
    }

    void clearItem(){
        stack.amount = 0;
    }

    boolean acceptsItem(Item item){
        return !hasItem() || item == stack.item && stack.amount + 1 <= itemCapacity();
    }

    boolean hasItem(){
        return stack.amount > 0;
    }

    void addItem(Item item){
        addItem(item, 1);
    }

    void addItem(Item item, int amount){
        stack.amount = stack.item == item ? stack.amount + amount : amount;
        stack.item = item;
        stack.amount = Mathf.clamp(stack.amount, 0, itemCapacity());
    }

    int maxAccepted(Item item){
        return stack.item != item && stack.amount > 0 ? 0 : itemCapacity() - stack.amount;
    }
}