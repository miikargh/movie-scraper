(ns movie-scraper.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.java.io :as io]
            [clojure.string :as s]))


(def base-url "https://www.themoviedb.org")
(def last-scrape-file "last_scraped.txt")

(defn fetch-url
  "Fetches the HTML content from given url"
  [url]
  (html/html-resource (java.net.URL. url)))


(defn scrape-movie-urls
  "Returns a list of movie urls"
  [page]
  (map first (map #(html/attr-values % :href) (html/select page [:.result]))))


(defn scrape-movie-description
  "Scrapes movie description form given movie page"
  [page]
  (s/replace
   (first (get (first (html/select page [:.overview :p])) :content))
   #"\n"
   " "))


(defn scrape-movie-title
  "Returns the title of the movie of the given movie page."
  [page]
  (first (get (first (html/select page [:.header :.title :h2])) :content)))


(defn title->file
  "Turns name of a movie to a suitable filaname."
  [title]
  (str
   (s/replace (s/lower-case (apply str (re-seq #"[a-zA-Z0-9\s]" title)))
              #"\s"
              "_")
   ".txt"))


(defn scrape-movie
  "Scrapes and returns movie name and description."
  [page]
  {:title (scrape-movie-title page)
   :desc (scrape-movie-description page)})


(defn get-next-url
  "Builds the next url from given url."
  [url]
  (let [params (s/split url #"\?page=")
        base-url (first params)
        cur-page-num (second params)]
    (if (not (nil? cur-page-num))
      (str base-url "?page=" (+ 1 (Integer. cur-page-num)))
      (str base-url "?page=2"))))


(defn save-movie
  "Saves given movie to a file in given directory."
  [movie dir]
  (let [{title :title
         desc :desc} movie
        fname (title->file title)
        path (str dir fname)]
    (io/make-parents path)
    (spit path (str title "\n\n" desc))))


(defn scrape-movies
  "Scrapes movie descriptions from a movie listing page."
  ([start-url] (scrape-movies start-url 0))
  ([start-url num-total]
   (let [page (fetch-url start-url)
         movie-urls (map #(str base-url %) (scrape-movie-urls page))
         movie-count (count movie-urls)
         next-url (get-next-url start-url)
         new-total (+ num-total movie-count)]
     (println movie-count "movies found from" start-url ". Total: " new-total)
     (if (= 0 (count movie-urls))
       (println "Scraping done.")
       (do
         (spit last-scrape-file start-url)
         (doseq [url movie-urls]
           (future (save-movie (scrape-movie (fetch-url url)) "data/")))
         (recur next-url new-total))))))


(defn get-last-scraped-url
  "Reads the url form last-scraped-file." []
  (if (.exists (io/as-file last-scrape-file))
    (s/trim (slurp last-scrape-file))))


(defn -main [& args]
  (if (not-empty args)
    (scrape-movies (first args))
    (do
      (let [last-scraped (get-last-scraped-url)]
        (if last-scraped
          (scrape-movies last-scraped)
          (scrape-movies (str base-url "/movie")))))))
