import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  template: `<router-outlet />`,
  styles: [`:host { display: block; min-height: 100vh; background: var(--bg); }`]
})
export class App {}
