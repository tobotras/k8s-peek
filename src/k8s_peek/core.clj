(ns k8s-peek.core
  (:require [kubernetes-api.core :as k8s]
            [yaml.core :as yaml]
            [clojure.java.io :as io]
            [clojure.pprint :as pp])
  (:gen-class))

(defn read-cluster-config [name]
  (let [kubecfg (yaml/from-file (io/file (System/getenv "HOME") ".kube" "config"))
        fetch-key (fn [section keys]
                    (->> kubecfg
                      section
                      (some #(when (= (:name %) name)
                               (get-in % keys)))))]
    [(fetch-key :clusters [:cluster :server]) (fetch-key :users [:user :token])]))

(defn show-node [node]
  ;;(pp/pprint node)
  (println (format "Name: '%s', addr: %s, platform: %s/%s"
                   (get-in node [:metadata :name])
                   (some #(when (= (:type %) "InternalIP") (:address %))
                         (get-in node [:status :addresses]))
                   (get-in node [:status :nodeInfo :operatingSystem])
                   (get-in node [:status :nodeInfo :architecture]))))

(defn show-deployment [d]
  ;;(pp/pprint d)
  (println "Name:" (get-in d [:metadata :name])))

(defn show-pod [pod]
  (println "Name:" (get-in pod [:metadata :name]))
  (when-let [containers (get-in pod [:spec :containers] nil)]
    (println "   ** Containers:")
    (doseq [c containers]
      (println (format "     Name: '%s', image '%s'" (:name c) (:image c)))))
  (when-let [volumes (get-in pod [:spec :volumes])]
    (println "   ** Volumes:")
    (doseq [v volumes]
      (println "     Name:" (:name v)))))

(defn show-volume [vol]
  (println "Name:" (get-in vol [:metadata :name]))
  (println (format "Size: %s, path: %s, mode: %s"
                   (get-in vol [:spec :capacity :storage])
                   (get-in vol [:spec :hostPath :path])
                   (get-in vol [:spec :volumeMode]))))

(defn show-service [svc]
  (println "Name:" (get-in svc [:metadata :name]))
  (pp/pprint (select-keys (:spec svc) [:type :clusterIP :ports]))
  )

(defn -main
  [& args]
  (when-not (= (count args) 1)
    (println "Specify cluster name")
    (System/exit 1))
  (let [[endpoint token] (read-cluster-config (first args))]
    (when-not (and endpoint token)
      (println "Cannot find cluster" (first args) "in kube config")
      (System/exit 2))
      
    (println "Cluster API endpoint:" endpoint)
    (when-let [cluster (k8s/client endpoint {:token token
                                             :insecure? true})]
      (let [list-objects (fn [kind]
                           (get (k8s/invoke cluster {:kind kind
                                                     :action :list
                                                     :request {:namespace "default"}})
                                :items))
            show-objects (fn [kind show-fn]
                           (when-let [nodes (list-objects kind)]
                             (println (str "------- " (name kind) "s:"))
                             (doseq [node nodes]
                               (show-fn node))))]
        (show-objects :Node show-node)
        (show-objects :Deployment show-deployment)
        (show-objects :Pod show-pod)
        (show-objects :PersistentVolume show-volume)
        (show-objects :Service show-service)))))
