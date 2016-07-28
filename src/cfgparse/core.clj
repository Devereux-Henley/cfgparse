(ns cfgparse.core
 (:require [dk.ative.docjure.spreadsheet :as xl]
           [clojure.java.io :as io]
           [clojure.pprint :refer [pprint]]
           [clojure.string :refer [split starts-with? blank? trim]])
 (:gen-class))

(def outputfile
  "output.xlsx")

(defn export
  "exports input data to a .xlsx file"
  [outputdata title]
  (if (.exists (io/as-file outputfile))
    (let [wb (xl/load-workbook outputfile)]
      (xl/add-rows! (xl/add-sheet! wb title) outputdata)
      (xl/save-workbook! outputfile wb))
    (let [wb (xl/create-workbook title
                                 outputdata)]
      (xl/save-workbook! outputfile wb))))

(defn chunkfile 
  "Chunks a file into a map of definitions ordered by number"
  [lines accumulator current-count]
  (if-let [line (first lines)]
    (let [trimmedline (trim line)]
      (if (or (starts-with? trimmedline "define") (starts-with? trimmedline "#") (blank? trimmedline))
        (chunkfile (rest lines) accumulator current-count)
        (if (starts-with? trimmedline "}")
          (chunkfile (rest lines) (assoc accumulator (inc current-count) {}) (inc current-count))
          (let [bits (split trimmedline #"\s+" 2)
                stringbits (map str bits)
                linekey (keyword (first stringbits))
                linevalue (trim (last stringbits))]
            (chunkfile (rest lines) (assoc-in accumulator [current-count linekey] linevalue) current-count)))))
    accumulator))

(defn chunkfile-wrap
  "Wraps chunkfile by opening input file and initializing map at a specified count"
  [current-count filename]
  (with-open [rdr (io/reader filename)]
    (let [file (line-seq rdr)]
      (chunkfile (rest file) {current-count {}} current-count))))
        

(defn readin
  "takes a list of filenames and reads the files into a map"
  [filenames]
  (loop [file (first filenames)
         files (rest filenames)
         acc {}]
    (if file
      (->>  file
           (chunkfile-wrap (count acc))
           (merge acc)
           (recur (first files) (rest files)))
      acc)))
        
(defn create-entry
  [headers arr input-chunk]
  (for [header headers]
    (if-let [chunkval ((keyword header) input-chunk)]
      (conj (get arr (.indexOf headers header)) chunkval)
      (conj (get arr (.indexOf headers header)) ""))))

;;Need to only remove completely empty rows. Not partials.
(defn remove-empty-entries
  [colls]
  (filter #(not (empty? %)) colls))

(defn columns-to-rows
  [colls]
  (partition (count colls) (apply interleave colls)))

;;Need to only remove completely empty rows. Not partials
(defn clean-row-entries
  [rows]
  (apply (fn [& colls] (for [coll colls] (filter #(not (empty? %)) coll))) rows))

(defn maptoarr
  [input-map headers]
  (let [arr (vec (for [header headers] [header]))
        input-count (count input-map)]
    (loop [cnt 0
           output-array arr]
      (if (> cnt input-count)
        output-array
        (let [current-chunk (get input-map cnt)
              new-entry-arr (vec (create-entry headers output-array current-chunk))
              incremented-count (inc cnt)]
          (recur incremented-count new-entry-arr))))))

(defn- main
  [& args]
  (with-open [rdr (io/reader "conf.txt")]
    (doseq [line (line-seq rdr)]
      (let [params (split line #":")
            filenames (split (get params 0) #",")
            headers (split (get params 1) #",")
            title (get params 2)]
        (-> filenames
          (readin)
          (maptoarr headers)
          (columns-to-rows)
          (export title))))))

(defn- test-main
  "Just for testing functionality of main w/o doseq"
  []
  (-> ["tmp/checkcommands.cfg" "tmp/misccommands.cfg" "tmp/pagerduty_nagios.cfg"]
    (readin)
    (maptoarr ["command_name" "command_line"])
    (columns-to-rows)
    (export "commands"))) 
