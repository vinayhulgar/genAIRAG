package com.support.assistant.config;

import com.support.assistant.agent.WorkflowEvent;
import com.support.assistant.agent.WorkflowStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.state.State;

/**
 * Configuration for the agent workflow state machine.
 * Defines states, transitions, and guards for the multi-agent orchestration workflow.
 */
@Configuration
@EnableStateMachineFactory
@Slf4j
public class StateMachineConfig extends EnumStateMachineConfigurerAdapter<WorkflowStatus, WorkflowEvent> {
    
    @Override
    public void configure(StateMachineConfigurationConfigurer<WorkflowStatus, WorkflowEvent> config)
            throws Exception {
        config
            .withConfiguration()
            .autoStartup(false)
            .listener(stateMachineListener());
    }
    
    @Override
    public void configure(StateMachineStateConfigurer<WorkflowStatus, WorkflowEvent> states)
            throws Exception {
        states
            .withStates()
            .initial(WorkflowStatus.INITIALIZED)
            .state(WorkflowStatus.PLANNING)
            .state(WorkflowStatus.RETRIEVING)
            .state(WorkflowStatus.COMPRESSING)
            .state(WorkflowStatus.GENERATING)
            .state(WorkflowStatus.VALIDATING)
            .state(WorkflowStatus.RETRYING)
            .end(WorkflowStatus.COMPLETE)
            .end(WorkflowStatus.FAILED);
    }
    
    @Override
    public void configure(StateMachineTransitionConfigurer<WorkflowStatus, WorkflowEvent> transitions)
            throws Exception {
        transitions
            // Start workflow
            .withExternal()
                .source(WorkflowStatus.INITIALIZED)
                .target(WorkflowStatus.PLANNING)
                .event(WorkflowEvent.START)
                .and()
            
            // Planning -> Retrieving
            .withExternal()
                .source(WorkflowStatus.PLANNING)
                .target(WorkflowStatus.RETRIEVING)
                .event(WorkflowEvent.PLANNING_COMPLETE)
                .and()
            
            // Retrieving -> Compressing
            .withExternal()
                .source(WorkflowStatus.RETRIEVING)
                .target(WorkflowStatus.COMPRESSING)
                .event(WorkflowEvent.RETRIEVAL_COMPLETE)
                .and()
            
            // Compressing -> Generating
            .withExternal()
                .source(WorkflowStatus.COMPRESSING)
                .target(WorkflowStatus.GENERATING)
                .event(WorkflowEvent.COMPRESSION_COMPLETE)
                .and()
            
            // Generating -> Validating
            .withExternal()
                .source(WorkflowStatus.GENERATING)
                .target(WorkflowStatus.VALIDATING)
                .event(WorkflowEvent.GENERATION_COMPLETE)
                .and()
            
            // Validating -> Complete (high confidence)
            .withExternal()
                .source(WorkflowStatus.VALIDATING)
                .target(WorkflowStatus.COMPLETE)
                .event(WorkflowEvent.VALIDATION_COMPLETE)
                .and()
            
            // Validating -> Retrieving (low confidence, retry retrieval)
            .withExternal()
                .source(WorkflowStatus.VALIDATING)
                .target(WorkflowStatus.RETRIEVING)
                .event(WorkflowEvent.NEEDS_RETRIEVAL)
                .guard(context -> {
                    // Only allow retrieval retry if we haven't exceeded max retries
                    Integer retryCount = (Integer) context.getExtendedState()
                        .getVariables().get("retryCount");
                    int maxRetries = (Integer) context.getExtendedState()
                        .getVariables().getOrDefault("maxRetries", 3);
                    return retryCount == null || retryCount < maxRetries;
                })
                .and()
            
            // Error handling - any state can go to RETRYING
            .withExternal()
                .source(WorkflowStatus.PLANNING)
                .target(WorkflowStatus.RETRYING)
                .event(WorkflowEvent.ERROR)
                .and()
            .withExternal()
                .source(WorkflowStatus.RETRIEVING)
                .target(WorkflowStatus.RETRYING)
                .event(WorkflowEvent.ERROR)
                .and()
            .withExternal()
                .source(WorkflowStatus.COMPRESSING)
                .target(WorkflowStatus.RETRYING)
                .event(WorkflowEvent.ERROR)
                .and()
            .withExternal()
                .source(WorkflowStatus.GENERATING)
                .target(WorkflowStatus.RETRYING)
                .event(WorkflowEvent.ERROR)
                .and()
            .withExternal()
                .source(WorkflowStatus.VALIDATING)
                .target(WorkflowStatus.RETRYING)
                .event(WorkflowEvent.ERROR)
                .and()
            
            // Retry transitions - go back to the failed state
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.PLANNING)
                .event(WorkflowEvent.RETRY)
                .guard(context -> {
                    WorkflowStatus previousState = (WorkflowStatus) context.getExtendedState()
                        .getVariables().get("previousState");
                    return previousState == WorkflowStatus.PLANNING;
                })
                .and()
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.RETRIEVING)
                .event(WorkflowEvent.RETRY)
                .guard(context -> {
                    WorkflowStatus previousState = (WorkflowStatus) context.getExtendedState()
                        .getVariables().get("previousState");
                    return previousState == WorkflowStatus.RETRIEVING;
                })
                .and()
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.COMPRESSING)
                .event(WorkflowEvent.RETRY)
                .guard(context -> {
                    WorkflowStatus previousState = (WorkflowStatus) context.getExtendedState()
                        .getVariables().get("previousState");
                    return previousState == WorkflowStatus.COMPRESSING;
                })
                .and()
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.GENERATING)
                .event(WorkflowEvent.RETRY)
                .guard(context -> {
                    WorkflowStatus previousState = (WorkflowStatus) context.getExtendedState()
                        .getVariables().get("previousState");
                    return previousState == WorkflowStatus.GENERATING;
                })
                .and()
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.VALIDATING)
                .event(WorkflowEvent.RETRY)
                .guard(context -> {
                    WorkflowStatus previousState = (WorkflowStatus) context.getExtendedState()
                        .getVariables().get("previousState");
                    return previousState == WorkflowStatus.VALIDATING;
                })
                .and()
            
            // Skip transitions - skip failed operation and proceed to next
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.GENERATING)
                .event(WorkflowEvent.SKIP)
                .guard(context -> {
                    // Skip compression if it fails
                    WorkflowStatus previousState = (WorkflowStatus) context.getExtendedState()
                        .getVariables().get("previousState");
                    return previousState == WorkflowStatus.COMPRESSING;
                })
                .and()
            
            // Fail transitions - any state can fail
            .withExternal()
                .source(WorkflowStatus.RETRYING)
                .target(WorkflowStatus.FAILED)
                .event(WorkflowEvent.FAIL)
                .and()
            .withExternal()
                .source(WorkflowStatus.VALIDATING)
                .target(WorkflowStatus.FAILED)
                .event(WorkflowEvent.FAIL)
                .guard(context -> {
                    // Fail if max retries exceeded
                    Integer retryCount = (Integer) context.getExtendedState()
                        .getVariables().get("retryCount");
                    int maxRetries = (Integer) context.getExtendedState()
                        .getVariables().getOrDefault("maxRetries", 3);
                    return retryCount != null && retryCount >= maxRetries;
                });
    }
    
    /**
     * State machine listener for logging state transitions.
     */
    @Bean
    public StateMachineListenerAdapter<WorkflowStatus, WorkflowEvent> stateMachineListener() {
        return new StateMachineListenerAdapter<>() {
            @Override
            public void stateChanged(State<WorkflowStatus, WorkflowEvent> from, 
                                    State<WorkflowStatus, WorkflowEvent> to) {
                if (from != null && to != null) {
                    log.info("State transition: {} -> {}", 
                        from.getId(), to.getId());
                } else if (to != null) {
                    log.info("State machine started in state: {}", to.getId());
                }
            }
        };
    }
}
