package forge.card.ability.effects;

import java.util.HashMap;
import java.util.Map;

import forge.card.ability.AbilityUtils;
import forge.card.ability.SpellAbilityEffect;
import forge.card.spellability.SpellAbility;
import forge.card.trigger.Trigger;
import forge.card.trigger.TriggerHandler;

public class DelayedTriggerEffect extends SpellAbilityEffect {

    /* (non-Javadoc)
     * @see forge.card.abilityfactory.SpellEffect#resolve(java.util.Map, forge.card.spellability.SpellAbility)
     */
    @Override
    protected String getStackDescription(SpellAbility sa) {
        if (sa.hasParam("TriggerDescription")) {
            return sa.getParam("TriggerDescription");
        }

        return "";

    }

    @Override
    public void resolve(SpellAbility sa) {

        Map<String, String> mapParams = new HashMap<String, String>();
        sa.copyParamsToMap(mapParams);
        if (mapParams.containsKey("Cost")) {
            mapParams.remove("Cost");
        }

        if (mapParams.containsKey("SpellDescription")) {
            mapParams.put("TriggerDescription", mapParams.get("SpellDescription"));
            mapParams.remove("SpellDescription");
        }

        String triggerRemembered = null;

        // Set Remembered
        if (sa.hasParam("RememberObjects")) {
            triggerRemembered = sa.getParam("RememberObjects");
        }

        if (triggerRemembered != null) {
            for (final Object o : AbilityUtils.getDefinedObjects(sa.getSourceCard(), triggerRemembered, sa)) {
                sa.getSourceCard().addRemembered(o);
            }
        }

        final Trigger delTrig = TriggerHandler.parseTrigger(mapParams, sa.getSourceCard(), true);

        sa.getActivatingPlayer().getGame().getTriggerHandler().registerDelayedTrigger(delTrig);
    }
}
