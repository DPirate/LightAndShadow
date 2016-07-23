/*
 * Copyright 2016 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.ligthandshadow.dome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terasology.audio.events.PlaySoundEvent;
import org.terasology.entitySystem.entity.EntityManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.event.ReceiveEvent;
import org.terasology.entitySystem.systems.BaseComponentSystem;
import org.terasology.entitySystem.systems.RegisterSystem;
import org.terasology.entitySystem.systems.UpdateSubscriberSystem;
import org.terasology.itemRendering.components.AnimateRotationComponent;
import org.terasology.logic.characters.CharacterImpulseEvent;
import org.terasology.logic.characters.CharacterMoveInputEvent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector3f;
import org.terasology.registry.In;
import org.terasology.utilities.random.FastRandom;

@RegisterSystem
public class MagicDomeSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(MagicDomeSystem.class);

    private static final int WORLD_RADIUS = 500;

    @In
    private EntityManager entityManager;

    private Vector3f lastPos = Vector3f.zero();
    private EntityRef magicDomeEntity = EntityRef.NULL;
    private float updateDelta;
    private FastRandom random = new FastRandom();

    private Vector3f lastRotation;
    private Vector3f nextRotation;

    private float rotationInterval = 4.0f;
    private float rotationTransitionTime = 1.5f;
    private float baseRotationSpeed = 0.005f;

    @Override
    public void postBegin() {
        if (!entityManager.getEntitiesWith(MagicDomeComponent.class).iterator().hasNext()) {
            logger.info("Spawning magic dome!");

            magicDomeEntity = entityManager.create("lightAndShadowResources:magicDome");
            magicDomeEntity.setAlwaysRelevant(true);

            LocationComponent locationComponent = new LocationComponent(Vector3f.zero());
            locationComponent.setLocalScale(2 * WORLD_RADIUS * 1.01f);
            magicDomeEntity.saveComponent(locationComponent);

            AnimateRotationComponent rotationComponent = new AnimateRotationComponent();
            rotationComponent.rollSpeed = baseRotationSpeed;
            rotationComponent.pitchSpeed = baseRotationSpeed;
            rotationComponent.yawSpeed = baseRotationSpeed;
            magicDomeEntity.addComponent(rotationComponent);

            lastRotation = Vector3f.one().scale(baseRotationSpeed);
            nextRotation = new Vector3f(lastRotation);
        }
    }

    @ReceiveEvent(components = {LocationComponent.class})
    public void onCharacterMovement(CharacterMoveInputEvent moveInputEvent, EntityRef player, LocationComponent loc) {
        Vector3f pos = new Vector3f(loc.getWorldPosition());

        float distance = pos.length();

        float deltaDistance = TeraMath.fastAbs(pos.distance(lastPos));
        if (deltaDistance > 0.2f) {
            logger.info("CharacerMoveInputEvent: position: {} - distance from O: {}, delta: {}", pos, distance, deltaDistance);
            lastPos.set(pos);

            if (distance > WORLD_RADIUS) {
                logger.info("Sending player back!");
                Vector3f impulse = pos.normalize().invert();
                impulse.set(impulse.scale(64).setY(6));
                player.send(new CharacterImpulseEvent(impulse));

                player.send(new PlaySoundEvent(magicDomeEntity.getComponent(MagicDomeComponent.class).hitSound, 2f));
            }
        }
    }

    @Override
    public void update(float delta) {
        float newYaw;
        float newPitch;
        float newRoll;

        updateDelta += delta;

        if (updateDelta > rotationInterval) {
            updateDelta = 0;
            lastRotation.set(nextRotation);

            newYaw = Integer.signum(random.nextInt(-1, 1)) * random.nextFloat(0.9f, 1.1f);
            newPitch = Integer.signum(random.nextInt(-1, 1)) * random.nextFloat(0.9f, 1.1f);
            newRoll = Integer.signum(random.nextInt(-1, 1)) * random.nextFloat(0.9f, 1.1f);
            nextRotation = new Vector3f(newYaw, newPitch, newRoll).scale(baseRotationSpeed);
        }

        for (EntityRef entity : entityManager.getEntitiesWith(MagicDomeComponent.class, AnimateRotationComponent.class)) {
            Vector3f rotation = new Vector3f(lastRotation);

            if (updateDelta < rotationTransitionTime) {
                rotation = Vector3f.lerp(lastRotation, nextRotation, updateDelta / rotationTransitionTime);
            } else {
                rotation.set(nextRotation);
            }

            AnimateRotationComponent rotationComponent = entity.getComponent(AnimateRotationComponent.class);
            rotationComponent.yawSpeed = rotation.x;
            rotationComponent.pitchSpeed = rotation.y;
            rotationComponent.rollSpeed = rotation.z;

            entity.saveComponent(rotationComponent);
        }
    }
}
