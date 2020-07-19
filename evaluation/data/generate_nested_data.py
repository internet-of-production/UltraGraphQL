import random

def generate_data(n):
   file_name = "nested_person_data.ttl"
   with open(file_name, "w") as f:
      f.write("@prefix ex:    <http://example.org/> .\n")
      person = "ex:person_%s a ex:Person;\n   ex:relatedWith ex:person_%s.\n"
      for i in range(n):
         person_rel = random.randint(1,n)
         f.write(person % (i,person_rel))

if __name__ == "__main__":
   n=50
   generate_data(n)
