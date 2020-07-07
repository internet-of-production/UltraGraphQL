import json
import os
import time
import numpy as np
import pandas as pd
import requests
import re
import matplotlib.pyplot as plt
from urllib.parse import quote

from ipywidgets import FloatProgress
from IPython.display import display



if __name__ == "__main__":
  input_folder = './queries/one_service'
  HGQL_ENDPOINT = 'http://localhost:8098/graphql'
  SPARQL_ENDPOINT = 'https://dbpedia.org/sparql'

  hgql_queries_files = list(filter(lambda x: x.endswith('2.0.0.graphql'), os.listdir(input_folder)))
  hgql_queries_files.sort()
  rq_queries_files = list(filter(lambda x: x.endswith('.sparql'), os.listdir(input_folder)))
  hgql_queries_files.sort()

  hgql_queries = [open(input_folder + '/' + f, 'r').read() for f in hgql_queries_files]
  rq_queries = [open(input_folder + '/' + f, 'r').read() for f in rq_queries_files]


  def sparql_exec(query):
    url = SPARQL_ENDPOINT + "?default-graph-uri=http%3A%2F%2Fdbpedia.org&query=" + quote(
      query) + "&format=application%2Fsparql-results%2Bjson&CXML_redir_for_subjs=121&CXML_redir_for_hrefs=&timeout=30000&debug=on&run=+Run+Query+"

    payload = {}
    headers = {}

    response = requests.request("GET", url, headers=headers, data=payload)

    return response.text.encode('utf8')


  def hgql_exec(query, debug=False):

    payload = "{\"query\":\"" + (query.replace("\n", "")) + "\",\"variables\":{}}"
    headers = {
      'Content-Type': 'application/json'
    }

    response = requests.request("POST", HGQL_ENDPOINT, headers=headers, data=payload)

    return response.text.encode('utf8')


  def test_atom(query, typ='sparql'):
    start = time.time()
    if typ == 'sparql':
      r = sparql_exec(query)
    else:
      r = hgql_exec(query)

    end = time.time()
    timing = end - start

    return len(r), timing


  num_iteration = 20
  sleep_time = 0.5


  def mean_without_outliers(x):
    df = pd.DataFrame(x)
    Q1 = df.quantile(0.25)
    Q3 = df.quantile(0.75)
    IQR = Q3 - Q1

    return float(df[(df >= Q1 - 1.5 * IQR) | (df <= Q3 + 1.5 * IQR)].mean())


  test_results = []
  all_timings = []

  for i, hgql_query in enumerate(hgql_queries):
    # queries
    hgql_query = hgql_queries[i]
    rq_query = rq_queries[i]
    title = rq_queries_files[i].replace('.sparql', '')
    print(title)

    # progress bars
    fs = FloatProgress(min=0, max=num_iteration, description='SPARQL test:')
    display(fs)
    fj = FloatProgress(min=0, max=num_iteration, description='HGQL test:')
    display(fj)

    sparql_time = []
    sparql_results = 0
    hgql_time = []
    hgql_results = 0

    for j in np.arange(num_iteration):
      if (i + j) > 0:
        time.sleep(sleep_time)
      sparql_results, t = test_atom(rq_query, typ='sparql')
      sparql_time.append(t)
      fs.value += 1

    for j in np.arange(num_iteration):
      time.sleep(sleep_time)
      hgql_results, t = test_atom(hgql_query, typ='hgql')
      hgql_time.append(t)
      fj.value += 1

    ts = np.mean(sparql_time)
    tj = np.mean(hgql_time)
    time_diff = (tj - ts)
    time_diff_percent = 100 * time_diff / np.mean([ts, tj])

    test_results.append({
      'name': title,
      'time_sparql': ts,
      'result_sparql': sparql_results,
      'time_hgql': tj,
      'result_hgql': hgql_results,
      'time_diff': '{0:.2g}'.format(time_diff),
      'time_diff_percent': '{0:.2g}%'.format(time_diff_percent)
    });

    all_timings.append({
      'name': title,
      'hgql': hgql_time,
      'sparql': sparql_time
    })

  for i, hgql_query in enumerate(hgql_queries):
    tim = all_timings[i]

    a = np.array([np.hstack(tim['sparql']), np.hstack(tim['hgql'])]).transpose()
    print(a.t)
    df = pd.DataFrame(a, columns=['SPARQL', 'HGQL'])
    bp = df.boxplot(vert=False, figsize=(16, 4))
    fig = np.asarray(bp).reshape(-1)[0].get_figure()
    fig.suptitle(tim['name'])
    plt.show()

    pd.DataFrame.from_dict(test_results)
    test_results