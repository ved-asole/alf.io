import {Injectable} from "@angular/core";
import {HttpClient} from "@angular/common/http";
import {catchError, map, Observable, of} from "rxjs";

@Injectable()
export class UserService {
  constructor(private httpClient: HttpClient) {
  }

  public checkUserLoggedIn(): Observable<boolean> {
    return this.httpClient.get<{authenticated: boolean}>('/authentication-status', {
      observe: "response"
    }).pipe(
      map(resp => {
        return resp.status === 200
          && (resp.body?.authenticated || false);
      }),
      catchError((err) => {
        console.log('error!', err);
        return of(false);
      })
    )
  }

}
