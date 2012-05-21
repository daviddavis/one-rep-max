(ns one.repmax.config
  "Contains configuration for the One Rep Max application."
  (:require [net.cgrand.enlive-html :as html]))

(defn- production-transform [h]
  (html/transform h
                  [:#dev-links]
                  (html/substitute (html/html-snippet ""))))

(def ^{:doc "Configuration for the One Rep Max application."}
  config {:src-root "src"
          :app-root "src/app/cljs"
          :top-level-package "one"
          :js "public/javascripts"
          :dev-js-file-name "main.js"
          :prod-js-file-name "mainp.js"
          :dev-js ["goog.require('one.repmax.core');"
                   "goog.require('one.repmax.model');"
                   "goog.require('one.repmax.datastore_configuration.observer');"
                   "goog.require('one.repmax.datastore_configuration.view');"
                   "goog.require('one.repmax.exercises.observer');"
                   "goog.require('one.repmax.exercises.view');"
                   "goog.require('one.repmax.sets.observer');"
                   "goog.require('one.repmax.sets.view');"
                   "goog.require('one.repmax.logging');"
                   "one.repmax.core.start();one.repmax.core.repl();"]
          :prod-js ["one.repmax.core.start();"]
          :reload-clj ["/one/host_page"
                       "/one/reload"
                       "/one/templates"
                       "/one/repmax/config"
                       "/one/repmax/dev_server"]
          :prod-transform production-transform})
