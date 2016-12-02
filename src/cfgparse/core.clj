(ns cfgparse.core
  (:require [dk.ative.docjure.spreadsheet :as xl]
            [clojure.java.io :as io]
            [clojure.string :refer [split starts-with? blank? trim]]
            [cfgparse.config :as config])
  (:gen-class))

;;Name of excel output file
(def outputfile
  "output.xlsx")

(defn export
  "exports input data to a .xlsx file
  [['foo' 'bar']] 'sheet' => nil"
  [title outputdata]
  (if (.exists (io/as-file outputfile))
    (let [wb (xl/load-workbook outputfile)]
      (xl/add-rows! (xl/add-sheet! wb title) outputdata)
      (xl/save-workbook! outputfile wb))
    (let [wb (xl/create-workbook title outputdata)]
      (xl/save-workbook! outputfile wb))))

(defn chunkfile
  "Chunks a file into a map of definitions ordered by number
  '('foo\tbar') {} 0 => {0 {:foo 'bar'} 1 {}}"
  [[line & lines] acc cnt]
  (if line
    (let [trim-line (trim line)]
      (if (or (starts-with? trim-line "define") (starts-with? trim-line "#") (blank? trim-line))
        (recur lines acc cnt)
        (if (starts-with? trim-line "}")
          (recur lines acc (inc cnt))
          (let [[first-bit second-bit] (split trim-line #"\s+" 2)
                line-key (keyword first-bit)
                line-value (trim second-bit)]
            (recur lines (assoc-in acc [cnt line-key] line-value) cnt)))))
    acc))

(defn chunkfile-wrap
  "Wraps chunkfile by opening input file and initializing map at a specified count
  0 'foo.cfg' => {0 {:command_name 'wiz'}}"
  [current-count filename]
  (with-open [rdr (io/reader filename)]
    (let [file (line-seq rdr)]
      (chunkfile file {current-count {}} current-count))))

(defn read-in-files
  "takes a list of filenames and reads the files into a map.
  ['foo.cfg' 'bar.cfg'] => {0 {:command_name 'wiz'} 1 {:command_name 'woz'}}"
  ([filenames]
   (read-in-files filenames {}))
  ([[file & files] acc]
   (if file
     (->> file
          (chunkfile-wrap (count acc))
          (merge acc)
          (recur files))
     acc)))

(defn col-to-rows
  "Inverts a 2d vector.
  [['foo' 'bar'] ['boo' 'zar']] -> [['foo' 'boo'] ['bar' 'zar']]"
  [colls]
  (apply (partial mapv vector) colls))

(defn create-entry
  "Updates array with new entry from input-chunk that matches headers
  ['cat' 'dog'] [['foo'] ['bar']] {:cat 'baz'} => [['foo' 'baz'] ['bar' '']]"
  [headers arr input-chunk]
  (if (empty? input-chunk)
    arr
    (mapv
      conj
      arr
      (mapv #(if-let [hval ((keyword %) input-chunk)] hval "") headers))))

(defn split-entries
  "Creates duplicate rows based on comma split.
  '('foo' 'bar,baz') 1 => [['foo' 'bar] ['foo' 'baz']]"
  [arr idx]
  (loop [split-list (split (get arr idx) #",")
         acc (transient [])]
    (if-let [[split-item & remaining-list] split-list]
      (recur remaining-list (conj! acc (assoc arr idx split-item)))
      (persistent! acc))))

(defn map-to-arr
  "Converts file map to 2d vector
  ['foo' 'cat'] {0 {:foo 'bar' :cat 'dog'} 1 {:foo 'baz' :cat 'bat'}} => [['bar' 'baz'] ['dog' 'bat']]"
  [headers input-map]
  (let [arr (mapv #(vector %) headers)
        input-count (count input-map)]
    (loop [cnt 0
           output-array arr]
      (if (> cnt input-count)
        output-array
        (let [current-chunk (input-map cnt)
              new-arr (create-entry headers output-array current-chunk)
              incremented-count (inc cnt)]
          (recur incremented-count new-arr))))))

(defn- normal-process
  "Processes each definition from nagios files into a row. Takes a config/Sheet-Data record as input."
  [{:keys [files headers title dirs]}]
  (->> 
    (config/build-file-arr files dirs)
    (read-in-files)
    (map-to-arr headers)
    (col-to-rows)
    (export title)))

(defn- split-process
  "Splits a specific field on comma into new rows from within each nagios definition. Takes a config/Sheet-Data record as input."
  [{:keys [files headers title dirs split-item]}]
  (->> 
    (config/build-file-arr files dirs)
    (read-in-files)
    (map-to-arr headers)
    (col-to-rows)
    (mapv #(split-entries % (.indexOf headers split-item)))
    (mapcat identity)
    (export title)))

(comment "This multimethod dispatches based on whether or not we need to split a field into individual rows")
(defmulti process
  (fn [{:keys [split-item]}] split-item))

(defmethod process nil [sheet] (normal-process sheet))

(defmethod process :default [sheet] (split-process sheet))

(defn -main
  "Takes in a path to a config file, parses it, processes the files pointed to in the
  config, and exports the definitions to a .xlsx file."
  ([]
   (println "cfgparse requires a relative path to a configuration file as the first argument. e.g. 'cfgparse config.edn'"))
  ([conf-file-path & args]
   (io/delete-file outputfile true)
   (if (.exists (io/as-file conf-file-path))
     (let [sheets (config/read-config conf-file-path)]
       (doseq [sheet sheets]
         (process sheet))) 
     (println (str conf-file-path " could not be found.")))))
