import random
import string

def random_char(y):
   return ''.join(random.choice(string.ascii_letters) for x in range(y))

def generate_data(n_o, n_f):
   file_name = "growing_field_data.ttl"
   with open(file_name, "w") as f:
      f.write("@prefix ex:    <http://example.org/> .\n")
      person = "ex:person_%s a ex:Person;\n"
      for i in range(n_o):
         f.write(person % i)
         for j in range(1, n_f+1):
            if j == n_f:
               field = "   ex:field_%s \"%s\".\n"
            else:
               field = "   ex:field_%s \"%s\";\n"
            f.write(field % (j, random_char(10)))



if __name__ == "__main__":
   n_o=1000
   n_f=1000
   generate_data(n_o, n_f)
