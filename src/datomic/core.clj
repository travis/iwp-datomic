(ns datomic.core
  (:require [datomic.api :refer :all]))

("
* Datomic
- collection of immutable facts
- distributes read, write and query processing across different components
- http://docs.datomic.com/prodmode.png

** Reads
- from storage service
In-process memory
Transactor-local dev/free mode
SQL
DynamoDB
Riak
Couchbase
Infinispan

- facts never change, so they're cached extensively in 'peers' (like your app)
- can read current state, or state in past

** Writes
- go through 'transactor'


** OMG.
'The ability to query and access data locally has a profound effect on the code in a Peer. Query
results are directly accessible as simple data structures. There is no need to deal with the
command and resultset abstractions common to most traditional database APIs, nor the
object-relational mapping layers intended to hide them. A simple group of functions that
encapsulates building and executing queries and transactions is sufficient.

Peer code can use a given database value for an extended period of time without concern. Values
are immutable and provide a stable, consistent view of data for as long as a Peer needs
one. This is a marked difference from relational databases which require work to be done
quickly using a short-lived transaction. The code in a Peer can also access multiple database
values simultaneously, making it possible use different values to process different requests,
and to compare values from different points in time.'

"

 "
* setup
open http://blog.markwatson.com/2012/07/using-datomic-free-edition-in-lein.html
cd datomic
cp config/samples/free-transactor-template.properties mw.properties
bin/transactor mw.properties
open http://docs.datomic.com/tutorial.html
"

 comment

 (def uri "datomic:free://localhost:4334//iwp")
 (create-database uri)

 (def conn (connect uri))

 (def schema-tx (read-string (slurp "samples/seattle/seattle-schema.dtm")))

 ;; tell the transactor to load the schema
 @(transact conn schema-tx)

 ;; seattle online community data
 (def data-tx (read-string (slurp "samples/seattle/seattle-data0.dtm")))

 @(transact conn data-tx)

 (def results (q '[:find ?c :where [?c :community/name]] (db conn)))

 (prn (count results))
 (pprint results)

 (def id (ffirst results))

 (def first-community (-> conn db (entity id)))

 (pprint (keys first-community))
 (prn (:community/name first-community))

 (def communities (map #(entity (db conn) (first %)) results))

 ;; you can get attributes of entities
 (def names (map :community/name communities))

 ;; you can traverse relationships
 (def neighborhoods (map :community/neighborhood communities))

 (def neighborhood-names (map :neighborhood/name neighborhoods))

 (def capitol-hill (:neighborhood/name (first neighborhoods)))


 ;; you can follow relationships backwards: this gives all the
 ;; communities in capitol hill
 (def first-neighborhood-communities (map :community/name (:community/_neighborhood capitol-hill)))

 ;; you can return results directly
 (map second (q '[:find ?c ?n :where [?c :community/name ?n]] (db conn)))

 ;; and you don't actually need to return the entity
 (q '[:find ?n ?u
      :where
      [?c :community/name ?n]
      [?c :community/url ?u]] (db conn))

 ;; you can restrict results
 (q '[:find ?e ?c
      :where
      [?e :community/name "belltown"]
      [?e :community/category ?c]] (db conn))

 ;; you can query on enums
 (q '[:find ?n
      :where
      [?c :community/name ?n]
      [?c :community/type :community.type/twitter]] (db conn))

 ;; where clauses can join tables
 (q '[:find ?c_name
      :where
      [?c :community/name ?c_name]
      [?c :community/neighborhood ?n]
      [?n :neighborhood/district ?d]
      [?d :district/region :region/ne]] (db conn))


 ;; you can parameterize queries
 (def query '[:find ?n
              :in $ ?t
              :where
              [?c :community/name ?n]
              [?c :community/type ?t]])
 (q query (db conn) ":community.type/twitter")
 (q query (db conn) ":community.type/facebook-page")


 ;; you can use functions in queries
 (q '[:find ?n
      :where
      [?c :community/name ?n]
      [(.compareTo ?n "C") ?res]
      [(< ?res 0)]] (db conn))

 ;; you can do fulltext search

 (q '[:find ?n
      :where
      [(fulltext $ :community/name "Wallingford") [[?e ?n]]]] (db conn))

 ;; with joins, natch

 (q '[:find ?name ?cat
      :in $ ?type ?search
      :where
      [?c :community/name ?name]
      [?c :community/type ?type]
      [(fulltext $ :community/category ?search) [[?c ?cat]]]] (db conn)
      ":community.type/website",
      "food")



 ;;;; rules RULE ;;;

 ;; you can create rules that simplify queries drastically

 (def rules "[[[twitter ?c] [?c :community/type :community.type/twitter]]]")

 (q '[:find ?n :in $ % :where [?c :community/name ?n](twitter ?c)]
    (db conn)
    rules)

 (def rules "
 [[[twitter ?c] [?c :community/type :community.type/twitter]]
  [[region ?c ?r]
   [?c :community/neighborhood ?n]
   [?n :neighborhood/district ?d]
   [?d :district/region ?re]
   [?re :db/ident ?r]]]")

 (q '[:find ?n
      :in $ %
      :where
      [?c :community/name ?n]
      (region ?c :region/ne)] (db conn)
      rules)
 (q '[:find ?n
      :in $ %
      :where
      [?c :community/name ?n]
      (region ?c :region/sw)] (db conn)
      rules)

  ;; they can stack in sane ways
 (def social-media-rules "
 [[[social-media ?c]
   [?c :community/type :community.type/twitter]]
  [[social-media ?c]
   [?c :community/type :community.type/facebook-page]]]")

 (def social-media-compass-rules "
 [[[region ?c ?r]
   [?c :community/neighborhood ?n]
   [?n :neighborhood/district ?d]
   [?d :district/region ?re]
   [?re :db/ident ?r]]
  [[social-media ?c]
   [?c :community/type :community.type/twitter]]
  [[social-media ?c]
   [?c :community/type :community.type/facebook-page]]
  [[northern ?c] (region ?c :region/ne)]
  [[northern ?c] (region ?c :region/n)]
  [[northern ?c] (region ?c :region/nw)]
  [[southern ?c] (region ?c :region/sw)]
  [[southern ?c] (region ?c :region/s)]
  [[southern ?c] (region ?c :region/se)]]")

 (q '[:find ?n
      :in $ %
      :where
      [?c :community/name ?n]
      (northern ?c)
      (social-media ?c)] (db conn)
      social-media-compass-rules)

 ;; also:
 ;; - get database values from the past
 ;; - create a speculative next db value without "transacting"
 ;; - bulk upload data without worrying about dupes
 )
