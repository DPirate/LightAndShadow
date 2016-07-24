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
import org.terasology.logic.characters.CharacterMovementComponent;
import org.terasology.logic.location.LocationComponent;
import org.terasology.math.TeraMath;
import org.terasology.math.geom.Vector3f;
import org.terasology.registry.In;
import org.terasology.utilities.random.FastRandom;

@RegisterSystem
public class MagicDomeSystem extends BaseComponentSystem implements UpdateSubscriberSystem {

    private static final Logger logger = LoggerFactory.getLogger(MagicDomeSystem.class);

    private static final float ROTATION_INTERVAL = 4.0f;
    private static final float ROTATION_TRANSITION_TIME = 1.5f;
    private static final float BASE_ROTATION_SPEED = 0.005f;
    private static final float DOME_RADIUS = 500f;

    @In
    private EntityManager entityManager;
    private Vector3f lastPosition = Vector3f.zero();
    private EntityRef magicDomeEntity = EntityRef.NULL;
    private float updateDelta;

    private FastRandom random;
    private Vector3f lastRotation;

    private Vector3f nextRotation;

    @Override
    public void initialise() {
        random = new FastRandom();

        lastRotation = Vector3f.one().scale(BASE_ROTATION_SPEED);
        nextRotation = new Vector3f(lastRotation);
    }

    @Override
    public void postBegin() {
        if (!entityManager.getEntitiesWith(MagicDomeComponent.class).iterator().hasNext()) {
            logger.info("Spawning magic dome!");

            magicDomeEntity = entityManager.create("lightAndShadowResources:magicDome");
            magicDomeEntity.setAlwaysRelevant(true);

            MagicDomeComponent magicDomeComponent = magicDomeEntity.getComponent(MagicDomeComponent.class);
            magicDomeComponent.radius = DOME_RADIUS;
            magicDomeEntity.saveComponent(magicDomeComponent);

            LocationComponent locationComponent = new LocationComponent(Vector3f.zero());
            locationComponent.setLocalScale(2 * DOME_RADIUS * 1.01f);
            magicDomeEntity.saveComponent(locationComponent);

            AnimateRotationComponent rotationComponent = new AnimateRotationComponent();
            rotationComponent.rollSpeed = BASE_ROTATION_SPEED;
            rotationComponent.pitchSpeed = BASE_ROTATION_SPEED;
            rotationComponent.yawSpeed = BASE_ROTATION_SPEED;
            magicDomeEntity.addComponent(rotationComponent);
        }
    }

    @ReceiveEvent(components = {LocationComponent.class})
    public void onCharacterMovement(CharacterMoveInputEvent moveInputEvent, EntityRef player, LocationComponent playerLocation) {
        Vector3f position = new Vector3f(playerLocation.getWorldPosition());
        Vector3f positionDelta = new Vector3f(position).sub(lastPosition);

        for (EntityRef domeEntity : entityManager.getEntitiesWith(MagicDomeComponent.class, LocationComponent.class)) {
            LocationComponent domeLocationComponent = domeEntity.getComponent(LocationComponent.class);
            Vector3f domeCenter = domeLocationComponent.getWorldPosition();
            MagicDomeComponent domeComponent = domeEntity.getComponent(MagicDomeComponent.class);

            if (TeraMath.fastAbs(positionDelta.length()) > 0.1f) {
                float currentDistanceToCenter = position.distance(domeCenter);
                float lastDistanceToCenter = lastPosition.distance(domeCenter);

                if (currentDistanceToCenter > domeComponent.radius-0.4f && lastDistanceToCenter < domeComponent.radius) {
                    // player tries to escape the dome (inside -> outside)
                    logger.info("Pushing in!");
                    Vector3f impulse = new Vector3f(domeCenter).sub(position).normalize();
                    if (player.hasComponent(CharacterMovementComponent.class)) {
                        CharacterMovementComponent movementComponent = player.getComponent(CharacterMovementComponent.class);
                        impulse.scale(movementComponent.getVelocity().length() * 10f);
                    } else {
                        impulse.scale(64);
                    }
                    impulse.setY(6);

                    logger.info("Impulse: {} [{}]", impulse, impulse.length());
                    player.send(new CharacterImpulseEvent(impulse));
                    player.send(new PlaySoundEvent(domeComponent.hitSound, 2f));
                } else if (currentDistanceToCenter < domeComponent.radius+0.4f && lastDistanceToCenter > domeComponent.radius) {
                    // player tries to get inse the dome (outside -> inside)
                    logger.info("Pushing out!");
                    Vector3f impulse = new Vector3f(position).sub(domeCenter).normalize();
                    if (player.hasComponent(CharacterMovementComponent.class)) {
                        CharacterMovementComponent movementComponent = player.getComponent(CharacterMovementComponent.class);
                        impulse.scale(movementComponent.getVelocity().length() * 10f);
                    } else {
                        impulse.scale(64);
                    }
                    impulse.setY(6);

                    player.send(new CharacterImpulseEvent(impulse));
                    player.send(new PlaySoundEvent(domeComponent.hitSound, 2f));
                }

                lastPosition.set(position);
            }
        }
    }

    @Override
    public void update(float delta) {
        updateRotation(delta);
    }

    private void updateRotation(float delta) {
        float newYaw;
        float newPitch;
        float newRoll;

        updateDelta += delta;

        if (updateDelta > ROTATION_INTERVAL) {
            updateDelta = 0;
            lastRotation.set(nextRotation);

            newYaw = Integer.signum(random.nextInt(-1, 1)) * random.nextFloat(0.9f, 1.1f);
            newPitch = Integer.signum(random.nextInt(-1, 1)) * random.nextFloat(0.9f, 1.1f);
            newRoll = Integer.signum(random.nextInt(-1, 1)) * random.nextFloat(0.9f, 1.1f);
            nextRotation = new Vector3f(newYaw, newPitch, newRoll).scale(BASE_ROTATION_SPEED);
        }

        for (EntityRef entity : entityManager.getEntitiesWith(MagicDomeComponent.class, AnimateRotationComponent.class)) {
            Vector3f rotation = new Vector3f(lastRotation);

            if (updateDelta < ROTATION_TRANSITION_TIME) {
                rotation = Vector3f.lerp(lastRotation, nextRotation, updateDelta / ROTATION_TRANSITION_TIME);
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
