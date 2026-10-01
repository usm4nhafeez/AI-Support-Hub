# AI Support Hub --- Entity Documentation

This document summarizes the 11 entity classes currently present in
`com.aisupporthub.model.entity` and their intended responsibilities.

## 1. Entity Reference Table

  ------------------------------------------------------------------------------
                     \# Entity                Purpose           Example
  --------------------- --------------------- ----------------- ----------------
                      1 `ActionApproval`      Tracks approval   A refund
                                              requests and      awaiting
                                              decisions for     approval.
                                              sensitive         
                                              actions.          

                      2 `Agent`               Represents human  An agent
                                              support           assigned to a
                                              representatives   support
                                              handling          conversation.
                                              escalated issues. 

                      3 `AuditEvent`          Records           Recording an
                                              significant       approval
                                              system and        decision.
                                              business events   
                                              for               
                                              accountability.   

                      4 `Client`              Represents an     BookVault
                                              application or    registering to
                                              business          use support
                                              integrating with  APIs.
                                              AI Support Hub.   

                      5 `Conversation`        Represents a      A customer
                                              support session   asking about an
                                              between a         order.
                                              customer and the  
                                              system.           

                      6 `Customer`            Represents an end A BookVault user
                                              user associated   contacting
                                              with a client     support.
                                              application.      

                      7 `KnowledgeDocument`   Stores metadata   An FAQ or
                                              about knowledge   return-policy
                                              sources available document.
                                              to the assistant. 

                      8 `Message`             Stores an         A customer
                                              individual        question or
                                              message belonging assistant
                                              to a              response.
                                              conversation.     

                      9 `Ticket`              Tracks support    A refund issue
                                              issues requiring  under
                                              follow-up or      investigation.
                                              resolution.       

                     10 `ToolDefinition`      Defines           An order-status
                                              application tools lookup tool.
                                              or capabilities   
                                              available to the  
                                              assistant.        

                     11 `ToolExecution`       Records tool      Recording the
                                              execution         result of an
                                              attempts and      order lookup.
                                              their results.    
  ------------------------------------------------------------------------------

## 2. Typical Entity Flow

  ------------------------------------------------------------------------
                          Step Entity                Role
  ---------------------------- --------------------- ---------------------
                             1 `Client`              Identifies the
                                                     application
                                                     integrating with AI
                                                     Support Hub.

                             2 `Customer`            Identifies the end
                                                     user belonging to
                                                     that client.

                             3 `Conversation`        Represents the
                                                     customer's support
                                                     session.

                             4 `Message`             Stores messages
                                                     exchanged during the
                                                     conversation.

                             5 `KnowledgeDocument`   Provides
                                                     client-scoped
                                                     knowledge for
                                                     retrieval.

                             6 `ToolDefinition`      Defines an
                                                     application
                                                     capability the
                                                     assistant can
                                                     request.

                             7 `ToolExecution`       Records the execution
                                                     and result of a
                                                     requested tool.

                             8 `ActionApproval`      Tracks authorization
                                                     for sensitive
                                                     actions.

                             9 `Ticket`              Tracks issues that
                                                     need follow-up.

                            10 `Agent`               Supports human
                                                     handling and
                                                     escalation.

                            11 `AuditEvent`          Records relevant
                                                     events for auditing.
  ------------------------------------------------------------------------

## 3. Important Design Notes

  -----------------------------------------------------------------------
  Topic                               Description
  ----------------------------------- -----------------------------------
  Tenant isolation                    Client-owned data must be scoped
                                      and authorized by `clientId`.

  Entity mapping                      JPA entities map Java classes to
                                      relational tables or other
                                      supported relational mappings.

  Controlled tools                    The AI model should use explicit,
                                      validated application operations,
                                      not unrestricted database access.

  Sensitive actions                   Sensitive operations should require
                                      authorization and appropriate audit
                                      logging.

  Vector data                         Vector chunks and embeddings are
                                      generally managed through the
                                      configured Spring AI vector store
                                      and do not necessarily require
                                      separate JPA entities.

  Schema accuracy                     Exact fields, relationships, and
                                      constraints are defined by the Java
                                      entity annotations and database
                                      configuration.
  -----------------------------------------------------------------------
