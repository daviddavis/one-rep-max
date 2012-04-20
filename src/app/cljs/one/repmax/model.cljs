(ns one.repmax.model
  (:require [clojure.string :as string]
            [one.dispatch :as dispatch]
            [one.repmax.cookies :as cookies]
            [one.repmax.mongohq :as mongo]))

(def initial-state {:state :start
                    :datastore-configuration {:state :obtain-credentials, :api-key ""}
                    :exercises nil
                    :exercise-search {:query nil, :exercises nil}})

(def ^{:doc "An atom containing a map which is the application's current state."}
  state (atom initial-state))

(add-watch state :state-change-key
           (fn [_ _ o n]
             (dispatch/fire :model-change {:old o, :new n, :message (:last-message n)})))

;;;; Receiving events that update state

(dispatch/react-to #{:action}
                   (fn [_ d] (receive-action-message d)))

;; TODO Add docs => State only changes as the result of an action message
(defn receive-action-message [message]
  (swap! state apply-message message))

(defn apply-message [state message]
  (-> state
    (update-model message)
    (assoc :last-message message)))

(defmulti update-model (fn [state message] (:action message)))

;;;; Managing the Datastore Configuration

; The :datastore-configuration map contains the following elements:
;
;    1. :api-key => the user-provided API key for use in accessing MongoHQ
;    2. :state => the overall state of the datastore configuration (i.e.,
;       the state of the datastore initialization/verification process)
;    3. :error => a map containing a description of the error that occured
;       during the initialization process (in the :text key) and the state
;       in which the error occured (in the :occured-in-state key); this map
;       is only present if an error has occured
;
;  In a successful progression through the initialization process, the
;  atom will move through the following states:
;
;    :obtain-credentials ;; => the start state; waiting for API key
;    :verify-credentials ;; => API key present; need to verify API key works
;    :verify-database    ;; => API key verified; need to verify database exists
;    :verify-collections ;; => database present; need to verify or create collections
;    :ready              ;; => collections verified; datastore ready for use
;
;  If any step fails along the way, the state changes to :initialization-failed.

(defmethod update-model :datastore-configuration/initialize [state message]
  (-> state
    (assoc :state :datastore-configuration)
    (assoc :datastore-configuration (datastore-configuration-from-cookies))))

(defn datastore-configuration-from-cookies []
  (let [api-key (cookies/get-cookie :api-key)]
    (if (nil? api-key)
      (:datastore-configuration initial-state)
      (datastore-configuration-for-new-api-key api-key))))

(defn datastore-configuration-for-new-api-key [api-key]
  {:api-key api-key :state :verify-credentials})

(defmethod update-model :datastore-configuration/update [state {:keys [api-key]}]
  (assoc state :datastore-configuration (datastore-configuration-for-new-api-key api-key)))

(defmethod update-model :datastore-configuration/credentials-verified [state _]
  (assoc-in state [:datastore-configuration :state] :verify-database))

(defmethod update-model :datastore-configuration/database-verified [state _]
  (assoc-in state [:datastore-configuration :state] :verify-collections))

(defmethod update-model :datastore-configuration/collections-verified [state _]
  (assoc-in state [:datastore-configuration :state] :ready))

(defmethod update-model :datastore-configuration/initialization-failed [state {:keys [error]}]
  (let [previous-datastore-configuration-state (-> state :datastore-configuration :state)]
    (-> state
      (assoc-in [:datastore-configuration :state] :initialization-failed)
      (assoc-in [:datastore-configuration :error] {:text error, :occured-in-state previous-datastore-configuration-state}))))

;;; Managing the Exercise List

(defmethod update-model :datastore-configuration/ready [state _]
  (assoc state :state :exercise-list))

(defmethod update-model :exercises/initialized-from-datastore [state {:keys [exercises]}]
  (assoc state :exercises exercises))

(defmethod update-model :exercises/search [state {:keys [name]}]
  (let [search-results (find-exercises name (:exercises state))]
    (assoc state :exercise-search {:query name, :exercises search-results})))

(defn find-exercises [name exercises]
  (if (empty? name)
    exercises
    (filter-by-attribute exercises :name name)))

(defn filter-by-attribute [s attribute-name value-like]
  (let [normalize #(string/lower-case %)
        pattern (re-pattern (str ".*" (normalize value-like) ".*"))]
    (filter #(re-find pattern (normalize (attribute-name %))) s)))
