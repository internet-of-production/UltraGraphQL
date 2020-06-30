from requests import request




if __name__ == '__main__':
  url = "localhost:8098/graphql"

  payload = "{\"query\":\"{\\n  ex_Person(limit:100){\\n    _id\\n    ex_name\\n    ex_surname\\n    ex_age\\n  }\\n}\",\"variables\":{}}"
  headers = {
    'Content-Type': 'application/json'
  }

  response = request("POST", url, headers=headers, data=payload)


  print(response.text.encode('utf8'))
  print(response.elapsed)